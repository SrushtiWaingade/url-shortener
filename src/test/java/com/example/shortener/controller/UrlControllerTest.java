package com.example.shortener.controller;

import com.example.shortener.entity.Url;
import com.example.shortener.exception.AliasAlreadyExistsException;
import com.example.shortener.exception.ApiExceptionHandler;
import com.example.shortener.exception.InvalidUrlException;
import com.example.shortener.service.UrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UrlController.class)
@Import(ApiExceptionHandler.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlService service;

    @Test
    @DisplayName("returns 201 with the short url")
    void createsShortUrl() throws Exception {
        when(service.shorten(any(), any())).thenReturn(url("4c92", "https://example.com"));

        mockMvc.perform(post("/shorten")
                        .contentType("application/json")
                        .content("""
                                {"url": "https://example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/4c92"))
                .andExpect(jsonPath("$.code").value("4c92"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/4c92"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"));
    }

    @Test
    @DisplayName("missing url returns 400 with a message")
    void missingUrlReturns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("url is required"));
    }

    @Test
    @DisplayName("invalid url returns 400 with the reason")
    void invalidUrlReturns400() throws Exception {
        when(service.shorten(any(), any()))
                .thenThrow(new InvalidUrlException("only http and https URLs are allowed"));

        mockMvc.perform(post("/shorten")
                        .contentType("application/json")
                        .content("""
                                {"url": "ftp://example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("only http and https URLs are allowed"));
    }

    @Test
    @DisplayName("taken alias returns 409")
    void takenAliasReturns409() throws Exception {
        when(service.shorten(any(), eq("my-link")))
                .thenThrow(new AliasAlreadyExistsException("my-link"));

        mockMvc.perform(post("/shorten")
                        .contentType("application/json")
                        .content("""
                                {"url": "https://example.com", "alias": "my-link"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("alias 'my-link' is already taken"));
    }

    @Test
    @DisplayName("malformed json returns 400")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType("application/json")
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("request body is missing or malformed"));
    }

    @Test
    @DisplayName("alias with bad characters returns 400")
    void badAliasReturns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType("application/json")
                        .content("""
                                {"url": "https://example.com", "alias": "no spaces!"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // an alias with no separator could one day equal a generated code
    @Test
    @DisplayName("alias without a - or _ returns 400")
    void aliasWithoutSeparatorReturns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType("application/json")
                        .content("""
                                {"url": "https://example.com", "alias": "github"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("alias with a - is accepted")
    void aliasWithSeparatorIsAccepted() throws Exception {
        when(service.shorten(any(), any())).thenReturn(url("git-hub", "https://example.com"));

        mockMvc.perform(post("/shorten")
                        .contentType("application/json")
                        .content("""
                                {"url": "https://example.com", "alias": "git-hub"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("git-hub"));
    }

    @Test
    @DisplayName("known code redirects with 301")
    void redirectsWithMovedPermanently() throws Exception {
        when(service.findOriginalUrl("4c92"))
                .thenReturn(Optional.of("https://example.com/a/long/path?x=1"));

        mockMvc.perform(get("/4c92"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://example.com/a/long/path?x=1"));
    }

    @Test
    @DisplayName("unknown code returns 404")
    void unknownCodeReturns404() throws Exception {
        when(service.findOriginalUrl(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound());
    }

    private Url url(String code, String originalUrl) {
        Url url = new Url(originalUrl);
        url.setShortCode(code);
        return url;
    }
}