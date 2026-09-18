package com.example.twitter_challenge.controller;


import com.example.twitter_challenge.Service.interfaces.TweetService;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.request.UpdateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/tweet")
public class TweetController {
    private final TweetService tweetService;
    private final UserService userService;
    public TweetController(TweetService tweetService, UserService userService) {
        this.tweetService = tweetService;
        this.userService = userService;
    }

    @PostMapping
    public TweetResponse createTweet(@Valid @RequestBody CreateTweetRequest request) {
        return tweetService.createTweet(request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTweet(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (String) authentication.getPrincipal();
        UserResponse user = userService.findByUserName(username);
         tweetService.removeTweet(id, user.userId());
         return  ResponseEntity.noContent().build();
    }
    @GetMapping
    public Page<TweetResponse> findAllTweets(Pageable pageable) {
        return tweetService.findAllTweets(pageable);
    }

    @GetMapping("/{id}")
    public TweetResponse findTweetById(@PathVariable Long id) {
        return tweetService.findTweetById(id);
    }

    @PutMapping("/{id}")
    public TweetResponse updateTweet(@PathVariable Long id,@Valid @RequestBody UpdateTweetRequest request) {
        return tweetService.update(id, request);
    }
    @GetMapping("/search")
    public Page<TweetResponse> getTweetsByContentContaining(@RequestParam String keyword, Pageable pageable) {
        return tweetService.findByContentContaining(keyword, pageable);
    }
}
