package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Service.interfaces.StatisticsService;
import com.example.twitter_challenge.dto.response.StatisticsResponse;
import com.example.twitter_challenge.exception.StatisticsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StatisticsServicempl implements StatisticsService {
    private final StatisticsRepository statisticsRepository;

    public StatisticsServicempl(StatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public StatisticsResponse findById(Long id) {
        Statistics statistics = statisticsRepository.findById(id)
                .orElseThrow(() -> new StatisticsNotFoundException("Statistics with " + id + " not found"));
        return toResponse(statistics);
    }

    @Transactional(readOnly = true)
    @Override
    public StatisticsResponse findByTweetId(Long id) {
        Statistics statistics = statisticsRepository.findByTweetId(id)
                .orElseThrow(() -> new StatisticsNotFoundException("Statistics not found"));
        return toResponse(statistics);
    }

    @Transactional(readOnly = true)
    @Override
    public List<StatisticsResponse> findAll() {
        return statisticsRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private StatisticsResponse toResponse(Statistics statistics) {
        return new StatisticsResponse(
                statistics.getTweet().getId(),
                value(statistics.getViews()),
                value(statistics.getLikes()),
                value(statistics.getComments()),
                value(statistics.getBookmarks()),
                value(statistics.getRetweets()),
                value(statistics.getQuotes())
        );
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}
