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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
        tweetService.removeTweet(id, currentUserId());
        return ResponseEntity.noContent().build();
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
    public TweetResponse updateTweet(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTweetRequest request
    ) {
        return tweetService.update(id, request, currentUserId());
    }

    @GetMapping("/search")
    public Page<TweetResponse> getTweetsByContentContaining(
            @RequestParam String keyword,
            Pageable pageable
    ) {
        return tweetService.findByContentContaining(keyword, pageable);
    }

    /**
     * The ids of the original tweets the signed-in user has retweeted.
     *
     * <p>A retweet is stored as a separate tweet, so the retweet state of a tweet cannot be
     * derived from the tweet itself. This endpoint lets a client restore that state after a
     * reload. It always describes the authenticated user, no user id is accepted.</p>
     */
    @GetMapping("/retweets/me")
    public List<Long> getMyRetweetedTweetIds() {
        return tweetService.findRetweetedTweetIds(currentUserId());
    }

    @PostMapping("/{id}/retweet")
    public TweetResponse retweetTweet(@PathVariable Long id) {
        return tweetService.retweet(id, currentUserId());
    }

    @DeleteMapping("/{id}/retweet")
    public ResponseEntity<Void> unRetweet(@PathVariable Long id) {
        tweetService.unretweet(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/quote")
    public TweetResponse quoteTweet(
            @PathVariable Long id,
            @Valid @RequestBody CreateTweetRequest request
    ) {
        return tweetService.quoteTweet(id, currentUserId(), request.content());
    }

    @DeleteMapping("/{quoteTweetId}/quote")
    public ResponseEntity<Void> removeQuote(@PathVariable Long quoteTweetId) {
        tweetService.removeQuote(quoteTweetId, currentUserId());
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserResponse user = userService.findByEmail(authentication.getName());
        return user.userId();
    }
}
