package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.LikeService;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.request.CreateLikeRequest;
import com.example.twitter_challenge.dto.response.LikeResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/likes")
public class LikeController {
    private final LikeService likeService;
    private final UserService userService;

    @Autowired
    public LikeController(LikeService likeService, UserService userService) {
        this.likeService = likeService;
        this.userService = userService;
    }
    @PostMapping
    public LikeResponse createLike(@Valid @RequestBody CreateLikeRequest request) {
        return likeService.createLike(request);
    }
    @DeleteMapping
    public ResponseEntity<Void> deleteLike(@Valid @RequestBody CreateLikeRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (String) authentication.getPrincipal();
        UserResponse user = userService.findByUserName(username);
        likeService.removeLike(request,user.userId());
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
