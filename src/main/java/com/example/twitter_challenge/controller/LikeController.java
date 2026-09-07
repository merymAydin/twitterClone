package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.LikeService;
import com.example.twitter_challenge.dto.request.CreateLikeRequest;
import com.example.twitter_challenge.dto.response.LikeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/likes")
public class LikeController {
    private final LikeService likeService;

    @Autowired
    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }
    @PostMapping
    public LikeResponse createLike(@RequestBody CreateLikeRequest request) {
        return likeService.createLike(request);
    }
    @DeleteMapping
    public void deleteLike(@RequestBody CreateLikeRequest request) {
        likeService.removeLike(request);
    }
}
