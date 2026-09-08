package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.LikeService;
import com.example.twitter_challenge.dto.request.CreateLikeRequest;
import com.example.twitter_challenge.dto.response.LikeResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/likes")
public class LikeController {
    private final LikeService likeService;

    @Autowired
    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }
    @PostMapping
    public LikeResponse createLike(@Valid @RequestBody CreateLikeRequest request) {
        return likeService.createLike(request);
    }
    @DeleteMapping
    public ResponseEntity<Void> deleteLike(@Valid @RequestBody CreateLikeRequest request) {
        likeService.removeLike(request);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}")
    public LikeResponse getLike(@PathVariable Long id) {
        return likeService.findById(id);
    }
    @GetMapping
    public List<LikeResponse> getLikes() {
        return likeService.findAll();
    }
}
