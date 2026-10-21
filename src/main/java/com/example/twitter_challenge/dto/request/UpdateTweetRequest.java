package com.example.twitter_challenge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTweetRequest(@NotBlank @Size(max = 255) String content) {
}
