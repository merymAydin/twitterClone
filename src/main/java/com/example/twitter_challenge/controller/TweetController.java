package com.example.twitter_challenge.controller;


import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Service.interfaces.TweetService;
import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.request.UpdateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
    public TweetResponse createTweet(@Valid @RequestBody CreateTweetRequest request) {
        return tweetService.createTweet(request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTweet(@PathVariable Long id) {
         tweetService.removeTweet(id);
         return  ResponseEntity.noContent().build();
    }
    @GetMapping
    public List<TweetResponse> findAllTweets() {
        return tweetService.findAllTweets();
    }
    @GetMapping("/{id}")
    public TweetResponse findTweetById(@PathVariable Long id) {
        return tweetService.findTweetById(id);
    }

    @PutMapping("/{id}")
    public TweetResponse updateTweet(@PathVariable Long id,@Valid @RequestBody UpdateTweetRequest request) {
        return tweetService.update(id, request);
    }
}
