package com.example.shortener.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path",
            "https://example.com/path?id=123#section",
            "https://sub.domain.co.uk/a/b/",
            "http://example.com:8080/p",
            "https://93.184.216.34/page"      // public IP is fine
    })
    @DisplayName("accepts public http and https urls")
    void acceptsValidUrls(String url) {
        assertThatCode(() -> UrlValidator.validate(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-a-url",
            "ftp://example.com",
            "javascript:alert(1)",
            "data:text/html,<script>",
            "mailto:someone@example.com",
            "http:///no-host"
    })
    @DisplayName("rejects anything that isn't an http or https url")
    void rejectsNonHttpUrls(String url) {
        assertThatThrownBy(() -> UrlValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://paypal.com@evil.com",
            "http://user:pass@example.com"
    })
    @DisplayName("rejects urls with user info")
    void rejectsUserInfo(String url) {
        assertThatThrownBy(() -> UrlValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080",
            "http://127.0.0.1/admin",
            "http://10.0.0.5",
            "http://172.16.0.1",
            "http://172.31.255.255",
            "http://192.168.1.1",
            "http://169.254.169.254/latest/meta-data",
            "http://0.0.0.0",
            "http://db.internal/health",
            "http://printer.local"
    })
    @DisplayName("rejects local and private addresses")
    void rejectsPrivateAddresses(String url) {
        assertThatThrownBy(() -> UrlValidator.validate(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://172.15.0.1",     // just below the private range
            "http://172.32.0.1",     // just above it
            "http://192.169.1.1"     // not 192.168
    })
    @DisplayName("does not over-block addresses near the private ranges")
    void allowsAddressesOutsidePrivateRanges(String url) {
        assertThatCode(() -> UrlValidator.validate(url)).doesNotThrowAnyException();
    }
}