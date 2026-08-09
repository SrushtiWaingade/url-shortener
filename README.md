# URL Shortener

A small URL-shortening service that takes a long URL and creates a short code for it.

For example:

```text
https://example.com/very/long/path
                ↓
              4c92
```

When someone visits `/4c92`, the service redirects them to the original URL.

**Technologies:** `Java 21` · `Spring Boot 3.5` · `MySQL 8` · `Docker`

### Example

```http
POST /shorten
Content-Type: application/json

{
  "url": "https://example.com/very/long/path"
}
```

```http
HTTP/1.1 201 Created

{
  "code": "4c92",
  "shortUrl": "http://localhost:8080/4c92",
  "originalUrl": "https://example.com/very/long/path"
}
```

Then:

```http
GET /4c92
```

```http
HTTP/1.1 301 Moved Permanently
Location: https://example.com/very/long/path
```

---

## Contents

* [Quick Start](#quick-start)
* [API](#api)
* [How It Works](#how-it-works)
* [Database](#database)
* [Design Decisions](#design-decisions)
* [Tests](#tests)
* [Known Limitations](#known-limitations)
* [Project Layout](#project-layout)
* [Why There Is No Migration Tool](#why-there-is-no-migration-tool)
* [In Short](#in-short)

---

# Quick Start

## Requirements

You only need:

* Docker

### Start the application

```bash
docker compose up --build
```

The first run can take a few minutes because Docker needs to download the images and Maven needs to download the project dependencies.

The application is ready when you see messages similar to:

```text
shortener-db   | ... ready for connections
shortener-app  | ... Started ShortenerApplication
```

### Create a short URL

Open another terminal and run:

```bash
curl -i -X POST localhost:8080/shorten \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/a/very/long/path"}'
```

### Stop the application

```text
Ctrl-C
```

### Remove the containers

```bash
docker compose down
```

---

## Running the Tests

The tests use a real MySQL database, so the database needs to be running.

You also need **Java 21** installed.

Start the database:

```bash
docker compose up -d db
```

Then run the tests:

```bash
./mvnw test
```

---

## Developing Locally

Rebuilding the Docker image every time you change code is slow.

For development, it is easier to run only MySQL in Docker and run the Spring Boot application directly:

```bash
docker compose up -d db
./mvnw spring-boot:run
```

The application reads the database host from `DB_HOST`.

If `DB_HOST` is not set, it automatically uses `localhost`.

This means the same configuration works both:

* Inside Docker
* When running the application directly on your machine

---

# API

## `POST /shorten`

Creates a short URL.

### Request

```bash
curl -i -X POST localhost:8080/shorten \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/a/very/long/path"}'
```

### Response

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/4c92
Content-Type: application/json
```

```json
{
  "code": "4c92",
  "shortUrl": "http://localhost:8080/4c92",
  "originalUrl": "https://example.com/a/very/long/path"
}
```

---

## Custom Aliases

You can also choose your own alias.

For example:

```bash
curl -s -X POST localhost:8080/shorten \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/launch","alias":"summer-launch"}'
```

Response:

```json
{
  "code": "summer-launch",
  "shortUrl": "http://localhost:8080/summer-launch",
  "originalUrl": "https://example.com/launch"
}
```

### Response Codes

| Status                      | Meaning                                                 |
| --------------------------- | ------------------------------------------------------- |
| `201 Created`               | Short URL was created                                   |
| `400 Bad Request`           | URL is missing, invalid, or points to a private address |
| `409 Conflict`              | The requested alias is already being used               |
| `500 Internal Server Error` | Something unexpected happened                           |

### Error Response

Errors are returned in the following format:

```json
{
  "error": "..."
}
```

For example, only `http` and `https` URLs are allowed:

```bash
curl -s -X POST localhost:8080/shorten \
  -H 'Content-Type: application/json' \
  -d '{"url":"ftp://example.com"}'
```

Response:

```json
{
  "error": "only http and https URLs are allowed"
}
```

### Alias Validation

Aliases may contain letters, numbers, `-`, and `_`, but must contain at least one `-` or `_`.

For example:

```text
summer-launch  ✓
my_link        ✓

github         ✗
docs           ✗
```

An invalid alias returns:

```json
{
  "error": "alias may only contain letters, numbers, - and _, and must include at least one - or _"
}
```

---

# `GET /{code}`

Looks up a short code and redirects to the original URL.

### Request

```bash
curl -i localhost:8080/4c92
```

### Response

```http
HTTP/1.1 301 Moved Permanently
Location: https://example.com/a/very/long/path
Cache-Control: private, max-age=300
```

### Response Codes

| Status                      | Meaning                                                             |
| --------------------------- | ------------------------------------------------------------------- |
| `301 Moved Permanently`     | The code exists and the `Location` header contains the original URL |
| `404 Not Found`             | No URL exists for that code                                         |
| `500 Internal Server Error` | Something unexpected happened                                       |

### Testing Tip

Don't use:

```bash
curl -L
```

`-L` tells `curl` to follow the redirect, so you will see the destination website instead of the response from this service.

Use:

```bash
curl -i
```

instead.

---

# How It Works

There are two main operations:

1. Creating a short URL
2. Redirecting from a short code

## Creating a Short URL

```text
POST /shorten
      │
      ▼
Validate the URL
      │
      ├── Invalid ───────────────→ 400
      │
      ▼
Is an alias provided?
      │
      ├── Yes
      │    │
      │    ▼
      │   Insert using the alias
      │    │
      │    ├── Alias already exists → 409
      │    │
      │    └── Success → 201
      │
      └── No
           │
           ▼
       Insert the URL
           │
           ▼
       Get the generated ID
           │
           ▼
       Convert ID to Base62
           │
           ▼
       Save the short code
           │
           ▼
          201
```

The insert and update happen inside **one database transaction**, so users never see the temporary row without a short code.

## Redirecting

```text
GET /4c92
      │
      ▼
Find 4c92 in the database
      │
      ├── Not found → 404
      │
      ▼
Return 301 redirect
      │
      ▼
Location: original URL
```

---

# Database

The application uses one table:

```sql
CREATE TABLE urls (
    id           BIGINT        AUTO_INCREMENT PRIMARY KEY,
    short_code   VARCHAR(32)   COLLATE utf8mb4_bin NULL,
    original_url VARCHAR(2048) NOT NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_urls_short_code UNIQUE (short_code)
) AUTO_INCREMENT = 1000000;
```

There are three important choices here.

## 1. `short_code` Can Be `NULL`

The short code is created from the database ID.

However, MySQL only gives us the ID **after** the row has been inserted.

The process is:

```text
Insert row
   ↓
Get ID
   ↓
Convert ID to Base62
   ↓
Update short_code
```

Both operations happen inside the same transaction.

## 2. `utf8mb4_bin` Makes Codes Case-Sensitive

MySQL normally uses a case-insensitive collation.

That could cause a problem because these two Base62 codes should be different:

```text
aB92x
Ab92X
```

Using `utf8mb4_bin` makes MySQL compare them exactly as written.

## 3. IDs Start at `1,000,000`

Without:

```sql
AUTO_INCREMENT = 1000000
```

the first IDs would be:

```text
1
2
3
4
```

Those would produce very short one-character codes.

Starting at one million means generated codes are longer from the beginning.

---

# Design Decisions

## 1. Short Codes Are Generated From the Database ID

A common approach is to generate a random string such as:

```text
a8F92xQ
```

Then check whether it already exists.

If it does, generate another one and try again.

That works, but it introduces collision checking and retry logic.

Instead, this project uses the database ID.

For example:

```text
ID
1000000
   ↓
Base62
   ↓
4c92
```

Every database ID is unique.

Therefore, every Base62 code is also unique.

### Main Advantage

**Codes are unique by design, not by chance.**

There is no need to check whether a generated code already exists.

Random codes can collide.

For example, with 7 random Base62 characters and one million links, the probability of at least one collision becomes significant.

The ID-based approach does not have this problem.

### The Complication

MySQL only provides the ID after inserting the row.

Therefore, the application:

1. Inserts the URL.
2. Gets the generated ID.
3. Converts the ID to Base62.
4. Updates the row with the generated code.

All four steps happen inside one transaction.

---

## 2. Custom Aliases Must Contain `-` or `_`

Generated Base62 codes contain only letters and numbers.

Custom aliases are required to contain at least one `-` or `_`.

For example:

```text
summer-launch   ✓
my_link         ✓

github          ✗
docs            ✗
```

This keeps generated codes and custom aliases in separate namespaces.

A generated code can never accidentally be the same as a custom alias.

### Why This Matters

Imagine aliases were allowed to be purely alphanumeric.

Someone could deliberately reserve:

```text
4c92
4c93
4c94
4c95
...
```

and potentially interfere with generated codes.

By requiring `-` or `_`, this problem disappears.

It also means we don't need a special list of reserved words.

Things such as:

```text
shorten
health
favicon.ico
robots.txt
```

are already rejected because they don't match the alias rules.

### Trade-off

Simple aliases such as:

```text
docs
github
```

aren't allowed.

Instead, you could use:

```text
docs-v2
git-hub
```

---

## 3. The Same URL Can Have Multiple Short Codes

If you shorten the same URL twice, you get two different codes.

For example:

```text
https://example.com
        ↓
abc1

https://example.com
        ↓
xyz2
```

This is intentional.

A shorten request means:

> Create a new short link.

It does not mean:

> Find an existing short link for this URL.

This is useful when the same website is used for different campaigns.

For example:

```text
summer-campaign
email-campaign
twitter-campaign
```

could all point to the same destination.

Trying to automatically reuse an existing code would also create additional questions, such as whether these URLs should be considered the same:

```text
https://example.com
https://example.com?utm_source=email
https://example.com?utm_source=twitter
```

For this project, creating a new code each time keeps the behavior simple.

---

## 4. The Database Decides Whether an Alias Is Available

A tempting approach is:

```text
1. Check if alias exists
2. If it doesn't, insert it
```

But this can fail when two requests happen at almost the same time.

For example:

```text
Request A: Check "summer-launch" → doesn't exist
Request B: Check "summer-launch" → doesn't exist

Request A: Insert → success
Request B: Insert → failure
```

The application therefore doesn't perform a separate "does this exist?" check.

Instead, the database has a unique constraint:

```sql
UNIQUE (short_code)
```

Both requests can try to insert.

The database allows one and rejects the other.

The application catches that database error and returns:

```text
409 Conflict
```

The integration test `ShortenerIntegrationTest` sends two requests at the same time and verifies:

```text
one request → 201
one request → 409
```

---

## 6. URL Validation Has Two Jobs

Validation is split into two parts.

### Bean Validation

The request DTO checks the basic structure:

* Is the URL present?
* Is it too long?
* Does the alias contain allowed characters?

### `UrlValidator`

The custom validator checks whether the URL itself makes sense for this service.

For example:

* Is it using `http` or `https`?
* Does it have a host?
* Is it pointing to a private address?

These are different types of validation, so keeping them separate makes the code easier to understand.

---

## 7. Database Credentials Are Simple on Purpose

The Docker setup uses default credentials such as:

```text
${DB_USER:app}
```

These credentials are not intended to be production secrets.

They exist so someone can clone the project and start it immediately without creating configuration first.

In a real production system, credentials should come from a proper secret-management system.

---

# Tests

Run the tests with:

```bash
docker compose up -d db
./mvnw test
```

The project has several levels of testing.

| Test Class                 | What It Tests                                                                                       |
|----------------------------|-----------------------------------------------------------------------------------------------------|
| `Base62Test`               | Base62 conversion, known values, URL-safe output, 10,000 unique codes, and `Long.MAX_VALUE`         |
| `UrlValidatorTest`         | URL schemes, missing hosts, `user@host`, private IP ranges, and addresses just outside those ranges |
| `UrlRepositoryTest`        | Database uniqueness and case-sensitive short codes                                                  |
| `UrlServiceTest`           | Real database behavior, generated codes, and shortening the same URL twice                          |
| `UrlControllerTest`        | HTTP status codes, headers, and JSON responses using a mocked service                               |
| `ShortenerIntegrationTest` | Overall integration tests with real HTTP calls                                                      |

# Known Limitations

This project intentionally keeps things simple.

There are several things that would need to be addressed before using it as a production service.

## 1. Short Codes Are Predictable

Because the code comes from the database ID, codes are sequential.

For example:

```text
4c92
4c93
4c94
4c95
```

If someone knows one code, they can potentially guess nearby codes.

That means someone could enumerate links.

A production system could hide the sequential ID using something like:

* A keyed permutation
* A Feistel network
* Sqids

This project doesn't implement that because it adds complexity that isn't necessary for the current goal.

---

## 2. There Is No Deduplication

If a client sends a request and the response gets lost, it might retry.

That can create two short links:

```text
Request 1 → abc1
Request 2 → xyz2
```

even though both point to the same URL.

One of those links may never be used.

Deduplication could solve this, but it introduces other questions around ownership and URL tracking.

For example, if two different users shorten the same URL, should they share the same short link?

For now, the project intentionally creates a new link every time.

---

## 3. Private-Address Protection Is Limited

The service blocks obvious private addresses such as:

```text
localhost
127.x.x.x
10.x.x.x
172.16.x.x - 172.31.x.x
192.168.x.x
169.254.x.x
*.internal
*.local
```

The `169.254.x.x` range is particularly important because cloud environments can use it for metadata services.

The application deliberately does **not** resolve hostnames during validation.

There are two reasons:

1. DNS lookups make the request slower.
2. DNS checking doesn't completely solve the problem.

A hostname could point to a public IP when the URL is checked and later point somewhere private.

The browser performs its own DNS lookup when the user clicks the link.

More generally, a URL shortener is an open-redirect service by design.

A production service would likely need additional protections such as:

* Safe-browsing checks
* Domain reputation checks
* An interstitial warning for unknown domains

---

## 4. There Is No Application-Level Redirect Cache

Every redirect currently requires a database lookup.

That's acceptable for a small application because the lookup uses the unique index and `301` responses are normally cached by browsers.

At higher traffic levels, a cache such as:

* Caffeine
* Redis

could be added.

This works well because short codes never change after they're created.

---

## 5. ID Generation Uses MySQL

The application gets IDs from MySQL's `AUTO_INCREMENT`.

This works correctly even if multiple application instances are running.

However, every new link still requires the database to generate an ID.

At very large scale, each application instance could receive a range of IDs in advance.

---

## 6. Several Production Features Are Missing

This project does not currently include:

* Link expiration
* Delete links
* Click analytics
* Authentication
* Rate limiting

There is also currently nothing preventing a single user from filling the database with a huge number of links.

These would be important additions for a real public-facing service.

---

# Project Layout

The project is organized by responsibility:

```text
src/main/java/com/example/shortener/
│
├── controller/
│   └── UrlController
│       Handles HTTP requests, responses, status codes, and headers
│
├── service/
│   └── UrlService
│       Contains the main business logic and transaction
│
├── repository/
│   └── UrlRepository
│       Handles database access
│
├── entity/
│   └── Url
│       Represents the database row
│
├── dto/
│   ├── ShortenRequest
│   │   Request data and validation
│   ├── ShortenResponse
│   │   Response returned after creating a short URL
│   └── ErrorResponse
│       Standard error response
│
├── exception/
│   ├── InvalidUrlException
│   │   Returned as 400
│   ├── AliasAlreadyExistsException
│   │   Returned as 409
│   └── ApiExceptionHandler
│       Converts exceptions into HTTP responses
│
└── util/
    ├── Base62
    │   Converts numbers into Base62 codes
    └── UrlValidator
        Validates URLs
```

Configuration and database setup live here:

```text
src/main/resources/
├── application.yml
└── schema.sql
```

The tests follow the same general structure as the main code.

---

# Why There Is No Migration Tool

The project uses:

```text
schema.sql
```

instead of something like Flyway or Liquibase.

That's intentional.

There is only one table, and the schema isn't expected to change during the lifetime of this project.

Spring Boot runs `schema.sql` when the application starts, and the SQL uses `IF NOT EXISTS`, so running it multiple times is safe.

The downside is that this doesn't provide a proper upgrade path.

For example, if a production database already has data and the schema changes, a migration tool would be the better solution.

For this small project, however, adding a migration framework would add complexity without providing much value.

---

# In Short

This project is a small URL-shortening service built with **Java 21, Spring Boot, MySQL, and Docker**.

The main ideas are:

* URLs are stored in MySQL.
* Each database ID is converted into a Base62 short code.
* This makes generated codes unique without random collision checks.
* Custom aliases are supported.
* Aliases must contain `-` or `_` so they cannot conflict with generated codes.
* The database handles alias conflicts safely using a unique constraint.
* Short URLs return `301` redirects.
* URL validation blocks invalid and obvious private/internal destinations.
* Tests cover everything from individual utility functions to full HTTP + database integration.
