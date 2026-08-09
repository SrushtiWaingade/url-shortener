package com.example.shortener.service;

import com.example.shortener.entity.Url;
import com.example.shortener.repository.UrlRepository;
import com.example.shortener.util.Base62;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class UrlService {

    private final UrlRepository repository;

    public UrlService(UrlRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Url shorten(String originalUrl, String alias) {

        return alias != null && !alias.isBlank() ? saveWithAlias(originalUrl, alias) : saveWithGeneratedCode(originalUrl);
    }

    private Url saveWithAlias(String originalUrl, String alias) {
        Url url = new Url(originalUrl);
        url.setShortCode(alias);

        return repository.saveAndFlush(url);
    }

    private Url saveWithGeneratedCode(String originalUrl) {

        Url url = repository.saveAndFlush(new Url(originalUrl));
        url.setShortCode(Base62.encode(url.getId()));

        return repository.saveAndFlush(url);
    }
}