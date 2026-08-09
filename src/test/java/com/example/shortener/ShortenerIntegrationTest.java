package com.example.shortener;

import com.example.shortener.dto.ShortenRequest;
import com.example.shortener.dto.ShortenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Real HTTP, real database, no mocks anywhere
// the concurrency test needs each request to actually commit, so rows survive the test.
// Every test uses a unique alias so they can't interfere.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShortenerIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    // Stop the client following redirects
    @BeforeEach
    void doNotFollowRedirects() {
        rest.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod)
                    throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });
    }

    @Test
    @DisplayName("shorten a url, then follow the code back to it")
    void roundTrip() {
        String target = "https://example.com/round-trip?x=1";

        ResponseEntity<ShortenResponse> created = shorten(target, null);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().originalUrl()).isEqualTo(target);

        ResponseEntity<Void> redirect = rest.getForEntity("/" + created.getBody().code(), Void.class);

        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        // byte-for-byte, not just "resolves to the same place"
        assertThat(redirect.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo(target);
    }

    @Test
    @DisplayName("unknown code returns 404")
    void unknownCodeReturns404() {
        assertThat(rest.getForEntity("/no-such-code", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("the same url shortened twice gets two different codes")
    void sameUrlGetsTwoCodes() {
        String target = "https://example.com/duplicate/" + unique();

        String first = shorten(target, null).getBody().code();
        String second = shorten(target, null).getBody().code();

        assertThat(first).isNotEqualTo(second);
        assertThat(rest.getForEntity("/" + first, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.MOVED_PERMANENTLY);
        assertThat(rest.getForEntity("/" + second, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.MOVED_PERMANENTLY);
    }

    @Test
    @DisplayName("a custom alias becomes the code")
    void customAliasIsUsed() {
        String alias = "my-link-" + unique();

        ResponseEntity<ShortenResponse> created = shorten("https://example.com/aliased", alias);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().code()).isEqualTo(alias);
        assertThat(rest.getForEntity("/" + alias, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.MOVED_PERMANENTLY);
    }

    @Test
    @DisplayName("an alias that's already taken returns 409")
    void takenAliasIsRejected() {
        String alias = "taken-" + unique();
        shorten("https://example.com/first", alias);

        ResponseEntity<String> second =
                rest.postForEntity("/shorten",
                        new ShortenRequest("https://example.com/second", alias),
                        String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("already taken");
    }

    @Test
    @DisplayName("a private address is rejected")
    void privateAddressIsRejected() {
        ResponseEntity<String> response =
                rest.postForEntity("/shorten",
                        new ShortenRequest("http://169.254.169.254/latest/meta-data", null),
                        String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }


    @Test
    @DisplayName("two requests racing for one alias: one 201, one 409")
    void concurrentAliasClaims() throws Exception {
        String alias = "race-" + unique();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        List<Future<HttpStatusCode>> attempts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            attempts.add(pool.submit(() -> {
                start.await();
                return rest.postForEntity("/shorten",
                                new ShortenRequest("https://example.com/race", alias),
                                String.class)
                        .getStatusCode();
            }));
        }

        start.countDown();

        List<HttpStatusCode> statuses = new ArrayList<>();
        for (Future<HttpStatusCode> attempt : attempts) {
            statuses.add(attempt.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses)
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
    }

    private ResponseEntity<ShortenResponse> shorten(String url, String alias) {
        return rest.postForEntity("/shorten", new ShortenRequest(url, alias), ShortenResponse.class);
    }

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}