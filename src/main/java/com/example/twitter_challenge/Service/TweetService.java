package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;


public interface TweetService {
    TweetResponse createTweet(CreateTweetRequest request);
}
