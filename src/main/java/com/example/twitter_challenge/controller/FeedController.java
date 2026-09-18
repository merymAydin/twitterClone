package com.example.twitter_challenge.controller;


import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Service.interfaces.FeedService;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/feed")
public class FeedController {
    private final FeedService feedService;
    private final UserService userService;

    public FeedController(FeedService feedService, UserService userService) {
        this.feedService = feedService;
        this.userService = userService;
    }

    @GetMapping
    public Page<TweetResponse> getTweets(Pageable pageable){
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();
        String userName = (String) authentication.getPrincipal();
        UserResponse user = userService.findByUserName(userName);
        return feedService.getTweets(user.userId(), pageable);
    }
}
