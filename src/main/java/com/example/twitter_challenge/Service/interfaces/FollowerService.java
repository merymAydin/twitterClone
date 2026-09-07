package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.dto.request.CreateFollowerRequest;
import com.example.twitter_challenge.dto.response.FollowerResponse;

import java.util.List;


public interface FollowerService {
    FollowerResponse createFollower(CreateFollowerRequest request);
    void removeFollower(CreateFollowerRequest request);
    List<FollowerResponse> findAllFollowers();
    FollowerResponse findFollowerById(Long id);
}
