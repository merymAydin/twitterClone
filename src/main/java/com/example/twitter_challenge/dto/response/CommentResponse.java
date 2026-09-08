package com.example.twitter_challenge.dto.response;

public record CommentResponse(Long commentId,Long userId, Long tweetId,String content) {
}
