package com.example.twitter_challenge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCommentRequest (@NotNull Long userId, @NotNull Long tweetId,@NotBlank String content) {
}
