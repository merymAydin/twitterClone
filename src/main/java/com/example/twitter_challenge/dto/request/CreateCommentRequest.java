package com.example.twitter_challenge.dto.request;

public record CreateCommentRequest (Long userId, Long tweetId, String content) {
}
