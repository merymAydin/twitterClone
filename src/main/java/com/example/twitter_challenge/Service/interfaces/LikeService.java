package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.dto.request.CreateLikeRequest;
import com.example.twitter_challenge.dto.response.LikeResponse;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

public interface LikeService {
    LikeResponse createLike(CreateLikeRequest request);
    void removeLike(CreateLikeRequest request, Long userId);
    LikeResponse findById(Long id);
    List<LikeResponse> findAll();

}
