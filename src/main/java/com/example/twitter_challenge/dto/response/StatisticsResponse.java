package com.example.twitter_challenge.dto.response;

import com.example.twitter_challenge.Entity.Tweet;

public record StatisticsResponse(
        Long tweetId,
        Long views,
        Long likes,
        Long comments,
        Long bookmarks,
        Long retweets
) {}