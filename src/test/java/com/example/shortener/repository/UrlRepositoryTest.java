package com.example.shortener.repository;

import com.example.shortener.entity.Url;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UrlRepositoryTest {

    @Autowired
    private UrlRepository repository;

    @Test
    @DisplayName("saves a url and finds it by code")
    void savesAndFindsByShortCode() {
        Url url = new Url("https://example.com/some/path");
        url.setShortCode("abc123");
        repository.saveAndFlush(url);

        assertThat(repository.findByShortCode("abc123"))
                .get().extracting(Url::getOriginalUrl)
                .isEqualTo("https://example.com/some/path");
    }

    @Test
    @DisplayName("unknown code returns empty")
    void unknownShortCodeReturnsEmpty() {
        assertThat(repository.findByShortCode("nope")).isEmpty();
    }

    @Test
    @DisplayName("same url can have several codes")
    void allowsTheSameUrlToBeStoredTwice() {
        Url first = new Url("https://example.com");
        first.setShortCode("code1");
        repository.saveAndFlush(first);

        Url second = new Url("https://example.com");
        second.setShortCode("code2");
        repository.saveAndFlush(second);

        assertThat(repository.findByShortCode("code1")).isPresent();
        assertThat(repository.findByShortCode("code2")).isPresent();
    }

    @Test
    @DisplayName("codes are case sensitive")
    void shortCodesAreCaseSensitive() {
        Url lower = new Url("https://example.com/lower");
        lower.setShortCode("aB92x");
        repository.saveAndFlush(lower);

        Url upper = new Url("https://example.com/upper");
        upper.setShortCode("Ab92X");
        repository.saveAndFlush(upper);

        assertThat(repository.findByShortCode("aB92x"))
                .get().extracting(Url::getOriginalUrl)
                .isEqualTo("https://example.com/lower");

        assertThat(repository.findByShortCode("Ab92X"))
                .get().extracting(Url::getOriginalUrl)
                .isEqualTo("https://example.com/upper");
    }

    @Test
    @DisplayName("duplicate code is rejected")
    void rejectsDuplicateShortCode() {
        Url first = new Url("https://example.com");
        first.setShortCode("taken");
        repository.saveAndFlush(first);

        Url second = new Url("https://different.com");
        second.setShortCode("taken");

        assertThatThrownBy(() -> repository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}