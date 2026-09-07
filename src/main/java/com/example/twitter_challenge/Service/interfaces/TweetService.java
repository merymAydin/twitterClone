package com.example.twitter_challenge.Service.interfaces;


import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;

import java.util.List;


public interface TweetService {
    TweetResponse createTweet(CreateTweetRequest request);
    void removeTweet(Long id);
    List<TweetResponse> findAllTweets();
    TweetResponse findTweetById(Long id);
}
