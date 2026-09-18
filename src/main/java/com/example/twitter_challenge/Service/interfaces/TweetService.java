package com.example.twitter_challenge.Service.interfaces;


import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.request.UpdateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;



public interface TweetService {
    TweetResponse createTweet(CreateTweetRequest request);
    void removeTweet(Long id,Long userId);
    Page<TweetResponse> findAllTweets(Pageable pageable);
    TweetResponse findTweetById(Long id);
    TweetResponse update(Long id, UpdateTweetRequest request);
    Page<TweetResponse> findByContentContaining(String keyword, Pageable pageable);
}
