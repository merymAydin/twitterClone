package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.dto.request.CreateFollowerRequest;
import com.example.twitter_challenge.dto.response.FollowerResponse;

public interface FollowerService {
    FollowerResponse createFollower(CreateFollowerRequest request);
}
