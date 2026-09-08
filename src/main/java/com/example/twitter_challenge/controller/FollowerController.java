package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.FollowerService;
import com.example.twitter_challenge.dto.request.CreateFollowerRequest;
import com.example.twitter_challenge.dto.response.FollowerResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/followers")
public class FollowerController {
    private FollowerService followerService;
    public FollowerController(FollowerService followerService) {
        this.followerService = followerService;
    }
    @PostMapping
    public FollowerResponse createFollower(@Valid @RequestBody CreateFollowerRequest request) {
        return followerService.createFollower(request);
    }
    @DeleteMapping
    public ResponseEntity<Void> deleteFollower(@Valid @RequestBody CreateFollowerRequest request) {
        followerService.removeFollower(request);
        return ResponseEntity.noContent().build();
    }
    @GetMapping
    public List<FollowerResponse> getAllFollowers(){
        return followerService.findAllFollowers();
    }
    @GetMapping("/{id}")
    public FollowerResponse getFollower(@PathVariable Long id){
        return followerService.findFollowerById(id);
    }

}
