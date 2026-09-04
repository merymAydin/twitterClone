package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.FollowerService;
import com.example.twitter_challenge.dto.request.CreateFollowerRequest;
import com.example.twitter_challenge.dto.response.FollowerResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/followers")
public class FollowerController {
    private FollowerService followerService;
    public FollowerController(FollowerService followerService) {
        this.followerService = followerService;
    }
    @PostMapping
    public FollowerResponse createFollower(@RequestBody CreateFollowerRequest request) {
        return followerService.createFollower(request);
    }
}
