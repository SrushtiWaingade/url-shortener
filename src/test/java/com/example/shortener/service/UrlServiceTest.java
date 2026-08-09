package com.example.shortener.service;

import com.example.shortener.entity.Url;
import com.example.shortener.repository.UrlRepository;
import com.example.shortener.util.Base62;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@SpringBootTest
@Transactional
class UrlServiceTest {

    @Autowired
    private UrlService service;

    @Autowired
    private UrlRepository repository;

    @Test
    @DisplayName("generated code is base62 of the row id")
    void generatesCodeFromRowId() {
        Url saved = service.shorten("https://example.com/one", null);

        assertThat(saved.getShortCode()).isEqualTo(Base62.encode(saved.getId()));
        assertThat(saved.getOriginalUrl()).isEqualTo("https://example.com/one");
    }

    @Test
    @DisplayName("custom alias is used as the code")
    void usesCustomAlias() {
        Url saved = service.shorten("https://example.com/two", "my-link");

        assertThat(saved.getShortCode()).isEqualTo("my-link");
    }

    @Test
    @DisplayName("blank alias is treated as no alias")
    void blankAliasGeneratesCode() {
        Url saved = service.shorten("https://example.com/three", "  ");

        assertThat(saved.getShortCode()).isEqualTo(Base62.encode(saved.getId()));
    }

    @Test
    @DisplayName("same url twice gives two different codes")
    void sameUrlGetsTwoCodes() {
        Url first = service.shorten("https://example.com/same", null);
        Url second = service.shorten("https://example.com/same", null);

        assertThat(first.getShortCode()).isNotEqualTo(second.getShortCode());
        assertThat(repository.findByShortCode(first.getShortCode())).isPresent();
        assertThat(repository.findByShortCode(second.getShortCode())).isPresent();
    }

    // proper 409 handling comes in a later commit
    @Test
    @DisplayName("taken alias is rejected by the unique constraint")
    void rejectsTakenAlias() {
        service.shorten("https://example.com/first", "taken-alias");

        assertThatThrownBy(() -> service.shorten("https://example.com/second", "taken-alias"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("invalid url is rejected before anything is saved")
    void rejectsInvalidUrl() {
        long before = repository.count();

        assertThatThrownBy(() -> service.shorten("javascript:alert(1)", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(repository.count()).isEqualTo(before);
    }
}