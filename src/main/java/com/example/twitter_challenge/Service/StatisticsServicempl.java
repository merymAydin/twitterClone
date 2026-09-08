package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Service.interfaces.StatisticsService;
import com.example.twitter_challenge.dto.response.StatisticsResponse;
import com.example.twitter_challenge.exception.StatisticsNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class StatisticsServicempl implements StatisticsService {
    private StatisticsRepository statisticsRepository;
    public StatisticsServicempl(StatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }


    @Override
    public StatisticsResponse findById(Long id) {
        Statistics statistics = statisticsRepository.findById(id).orElseThrow(() -> new StatisticsNotFoundException("Statistics with " + id + " not found"));
        return new StatisticsResponse(statistics.getTweet().getId(),statistics.getViews(), statistics.getLikes(), statistics.getComments(), statistics.getBookmarks(), statistics.getRetweets());
    }

    @Override
    public StatisticsResponse findByTweetId(Long id) {
        Statistics statistics = statisticsRepository.findByTweetId(id).orElseThrow(() -> new StatisticsNotFoundException("Statistics not found"));
        return new StatisticsResponse(statistics.getTweet().getId(),statistics.getViews(), statistics.getLikes(), statistics.getComments(), statistics.getBookmarks(), statistics.getRetweets());

    }

    @Override
    public List<StatisticsResponse> findAll() {
        return  statisticsRepository.findAll()
                .stream()
                .map(stats -> new StatisticsResponse(
                        stats.getTweet().getId(),
                        stats.getViews(),
                        stats.getLikes(),
                        stats.getComments(),
                        stats.getBookmarks(),
                        stats.getRetweets()
                ))
                .toList();
    }
}
