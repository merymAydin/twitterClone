package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.dto.request.CreateLikeRequest;
import com.example.twitter_challenge.dto.response.LikeResponse;

public interface LikeService {
    LikeResponse createLike(CreateLikeRequest request);

}
