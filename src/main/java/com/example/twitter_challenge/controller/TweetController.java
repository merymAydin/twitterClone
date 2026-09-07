package com.example.twitter_challenge.controller;


import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Service.interfaces.TweetService;
import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tweet")
public class TweetController {
    private final TweetService tweetService;
    public TweetController(TweetService tweetService) {
        this.tweetService = tweetService;
    }

    @PostMapping
    public TweetResponse createTweet(@RequestBody CreateTweetRequest request) {
        return tweetService.createTweet(request);
    }
    @DeleteMapping("/{id}")
    public void deleteTweet(@PathVariable Long id) {
         tweetService.removeTweet(id);
    }
    @GetMapping
    public List<TweetResponse> findAllTweets() {
        return tweetService.findAllTweets();
    }
    @GetMapping("/{id}")
    public TweetResponse findTweetById(@PathVariable Long id) {
        return tweetService.findTweetById(id);
    }
}
