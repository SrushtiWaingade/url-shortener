package com.example.shortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShortenRequest(

        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must be 2048 characters or fewer")
        String url,

        @Size(min = 3, max = 32, message = "alias must be 3 to 32 characters")
        @Pattern(regexp = "[A-Za-z0-9_-]+",
                message = "alias may only contain letters, numbers, - and _")
        String alias
) {
}