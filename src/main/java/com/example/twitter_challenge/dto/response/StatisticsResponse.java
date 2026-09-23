package com.example.twitter_challenge.dto.response;


public record StatisticsResponse(
        Long tweetId,
        Long views,
        Long likes,
        Long comments,
        Long bookmarks,
        Long retweets,
        Long quotes
) {}