package com.example.twitter_challenge.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommentRequest(@NotBlank String content) {
}
