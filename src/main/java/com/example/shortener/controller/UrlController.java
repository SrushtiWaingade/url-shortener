package com.example.shortener.controller;

import com.example.shortener.dto.ShortenRequest;
import com.example.shortener.dto.ShortenResponse;
import com.example.shortener.entity.Url;
import com.example.shortener.service.UrlService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

@RestController
public class UrlController {

    private final UrlService service;
    private final String baseUrl;

    public UrlController(UrlService service, @Value("${app.base-url}") String baseUrl) {
        this.service = service;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        Url saved = service.shorten(request.url(), request.alias());

        ShortenResponse body = new ShortenResponse(
                saved.getShortCode(),
                baseUrl + "/" + saved.getShortCode(),
                saved.getOriginalUrl());

        return ResponseEntity.created(URI.create(body.shortUrl())).body(body);
    }

    @GetMapping("/{code}")
    public ResponseEntity<ShortenResponse> redirect(@PathVariable String code){
        Optional<String> target = service.findOriginalUrl(code);

        if(target.isEmpty()){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
            .header(HttpHeaders.LOCATION, target.get())
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
            .build();

    }
}