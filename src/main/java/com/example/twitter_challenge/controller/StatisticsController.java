package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Service.interfaces.StatisticsService;
import com.example.twitter_challenge.dto.response.StatisticsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/statistics")
public class StatisticsController {
    private StatisticsService statisticsService;
    @Autowired
    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public List<StatisticsResponse> findAll(){
        return statisticsService.findAll();
    }
    @GetMapping("/{id}")
    public StatisticsResponse findById(@PathVariable Long id){
        return statisticsService.findById(id);
    }
    @GetMapping("/tweet/{tweetId}")
    public StatisticsResponse findByTweetId(@PathVariable Long tweetId){
        return statisticsService.findByTweetId(tweetId);
    }
}
