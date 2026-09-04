package com.example.twitter_challenge.controller;


import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Service.TweetService;
import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
