package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Service.interfaces.StatisticsService;

public class StatisticsServicempl implements StatisticsService {
    private StatisticsRepository statisticsRepository;
    public StatisticsServicempl(StatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }



    @Override
    public Statistics findById(Long id) {
        Statistics statistics = statisticsRepository.findById(id).orElse(null);
        return statistics;
    }

    @Override
    public Statistics findByTweetId(Long id) {
        Statistics statistics = statisticsRepository.findByTweetId(id).orElseThrow(()-> new throw StatisticsNotFoundException(""));

    }
}
