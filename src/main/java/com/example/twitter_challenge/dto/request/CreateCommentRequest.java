package com.example.twitter_challenge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotNull Long tweetId,
        @NotBlank @Size(max = 255) String content
) {
}
