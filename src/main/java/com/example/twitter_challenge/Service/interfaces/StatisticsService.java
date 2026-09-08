package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.dto.response.StatisticsResponse;

import java.util.List;
import java.util.Optional;

public interface StatisticsService {
    StatisticsResponse findById(Long id);
    StatisticsResponse findByTweetId(Long id);
   List<StatisticsResponse> findAll();

}
