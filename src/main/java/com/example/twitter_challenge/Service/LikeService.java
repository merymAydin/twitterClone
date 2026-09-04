package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Likes;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.dto.request.CreateLikeRequest;
import com.example.twitter_challenge.dto.response.LikeResponse;

import java.util.Optional;

public interface LikeService {
    LikeResponse createLike(CreateLikeRequest request);

}
