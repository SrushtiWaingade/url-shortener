# Write-up

## 1. What I asked the AI to do, and what I decided myself

I used the AI as a design opponent first and a typist second. Before writing
code I talked through the parts of the brief that were actually questions — how
to generate codes that can't collide, what to do about duplicate URLs, how to
handle two people claiming the same alias — and argued with the answers until I
had a position I could defend.

**Delegated:** project scaffolding, the Dockerfile and compose setup, JPA
boilerplate, test scaffolding, and prose for the README.

**Decided myself:** deriving codes from the row id instead of generating them
randomly; treating each shorten request as a new link rather than deduplicating;
requiring a separator in custom aliases so the two namespaces can't overlap;
letting the unique constraint arbitrate alias conflicts instead of checking
first; keeping validation inside the service rather than moving it to the
request boundary; and what to leave out.

I asked *"what happens if a generated code collides with an alias someone already
claimed?"* The first answer was to catch it and ask the client to retry. I
pushed on it and realised the same code path is a cheap denial of service —
claim `4c92`, `4c93`, `4c94` and every subsequent generated code fails. That's
what pushed me to separate the namespaces instead.

## 2. Where I overrode, corrected, or threw away the AI's output

**Flyway.** The AI set up Flyway for migrations and I used it for a commit
before removing it. Its value is versioned migrations — adding `V2__…` later and
having it apply to an existing database. I have one table that doesn't change,
so I was paying two dependencies and a set of rules for a feature I'd never use.
Spring Boot's built-in `schema.sql` keeps the part that matters: the schema
lives in a readable file rather than being inferred from annotations.

**Scope, repeatedly.** The suggested design had a `/api/links/{code}` metadata
endpoint, an `expires_at` column nothing read, an `is_custom` flag nothing used,
and an index for a query that didn't exist. I cut all of them. When the AI later
justified keeping a repository method that only the tests called — "a future
endpoint might want it" — I pointed out that was the same reasoning we'd
rejected for the unused column, and deleted it.

**An optimisation I backed out myself.** I replaced the lookup with a JPQL query
selecting only the URL column, so the service didn't have to map the entity. It
does fetch less. I reverted it: trading a plain derived query for a hand-written
one to save a few bytes on a table this size isn't worth the readability, and
`.map(Url::getOriginalUrl)` is one line.

**A bug the AI didn't catch until asked.** Reviewing the schema, I found MySQL 8
defaults to a case-insensitive collation. Base62 uses both cases, so `aB92x` and
`Ab92X` would have been treated as the same code — the unique constraint would
have rejected valid codes and lookups could return the wrong row. Fixed with
`COLLATE utf8mb4_bin`, with a test that fails without it.

## 3. The biggest trade-offs

**Sequential codes over unguessable ones.** Deriving the code from the row id
makes collisions structurally impossible rather than statistically unlikely —
there's no retry loop and no collision check anywhere. The cost is that codes
are enumerable: anyone with one can walk the table. The fix is a keyed
permutation on the id before encoding. I chose to name the limitation rather
than ship a mitigation whose correctness argument I'd be reciting instead of
explaining.

**Custom aliases must contain a `-` or `_`.** This makes it impossible for a
generated code to land on an existing alias, and it removed both a `try/catch`
and a reserved-word blocklist. The alternative — catching the clash and asking
the caller to retry — leaves the griefing attack open. The cost is real:
`docs` and `github` aren't valid aliases.

**A new code for every request, rather than deduplicating.** One destination can
have several links, which is what you want for separate campaigns, and it avoids
having to define whether `?utm_source=x` makes a URL different. The cost is that
a client retrying a timed-out request ends up with an orphan code nobody owns.
Deduplicating would fix that but couple unrelated users to a single link, so
delete or expiry would break one user's link on another's action.

## 4. What's missing, or what I'd do with another day

1. **A keyed permutation on the id**, so codes stop being enumerable. Smallest
   change with the largest effect on how the service behaves in the wild.
2. **Revisit dedup alongside delete and expiry.** They're the same problem: both
   need a notion of who owns a link. The right shape is a links table plus a
   separate ownership table.
3. **Rate limiting and an API key on `POST /shorten`.** Nothing currently stops
   one caller filling the table, and it's the gap dedup is often mistaken for.
4. **Click analytics** as an append-only events table written asynchronously —
   never a counter on the redirect path. Worth noting the numbers would
   undercount, because `301` responses are cached by the browser.
5. **A safe-browsing check and an interstitial** for untrusted destinations. A
   shortener is an open redirect by design; blocking bad URLs at write time
   isn't achievable, so the mitigations are detection and takedown.
6. **Pre-allocated id ranges per instance**, so inserts stop paying a round trip
   to the database for every code.