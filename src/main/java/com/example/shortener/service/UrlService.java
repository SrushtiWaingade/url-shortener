package com.example.shortener.service;

import com.example.shortener.entity.Url;
import com.example.shortener.exception.AliasAlreadyExistsException;
import com.example.shortener.repository.UrlRepository;
import com.example.shortener.util.Base62;
import com.example.shortener.util.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    private final UrlRepository repository;

    public UrlService(UrlRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Url shorten(String originalUrl, String alias) {
        UrlValidator.validate(originalUrl);

        Url saved = alias != null && !alias.isBlank()
                ? saveWithAlias(originalUrl, alias)
                : saveWithGeneratedCode(originalUrl);

        log.info("created code={} custom={} url={}",
                saved.getShortCode(), alias != null, saved.getOriginalUrl());

        return saved;
    }

    // Returns the URL rather than the entity, so Url stays behind the service.
    // readOnly lets Hibernate skip dirty checking — nothing here can change.
    @Transactional(readOnly = true)
    public Optional<String> findOriginalUrl(String code) {
        Optional<String> found = repository.findByShortCode(code).map(Url::getOriginalUrl);

        // Only misses are logged. A line per successful redirect would be the
        // busiest log in the system and would say nothing useful.
        if (found.isEmpty()) {
            log.info("lookup miss for code={}", code);
        }

        return found;
    }

    private Url saveWithAlias(String originalUrl, String alias) {
        Url url = new Url(originalUrl);
        url.setShortCode(alias);

        try {
            return repository.saveAndFlush(url);
        } catch (DataIntegrityViolationException e) {
            throw new AliasAlreadyExistsException(alias);
        }
    }

    private Url saveWithGeneratedCode(String originalUrl) {

        Url url = repository.saveAndFlush(new Url(originalUrl));
        url.setShortCode(Base62.encode(url.getId()));

        return repository.saveAndFlush(url);
    }
}