package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.dto.response.TweetResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FeedService {
    Page<TweetResponse> getTweets(Long userId,Pageable pageable);
}
