package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.Entity.Statistics;

public interface StatisticsService {
    public Statistics findById(Long id);
    public Statistics findByTweetId(Long id);

}
