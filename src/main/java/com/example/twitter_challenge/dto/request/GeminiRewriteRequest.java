package com.example.twitter_challenge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GeminiRewriteRequest(
        @NotBlank @Size(max = 5000) String content,
        @NotBlank @Size(max = 100) String tone
) {
}
