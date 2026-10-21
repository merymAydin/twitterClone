package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.dto.request.CreateCommentRequest;
import com.example.twitter_challenge.dto.request.UpdateCommentRequest;
import com.example.twitter_challenge.dto.response.CommentResponse;

import java.util.List;

public interface CommentService {
    CommentResponse createComment(CreateCommentRequest request);

    void removeComment(Long id, Long userId);

    List<CommentResponse> findAllComments();

    List<CommentResponse> findByTweetId(Long tweetId);

    CommentResponse findCommentById(Long id);

    CommentResponse update(Long commentId, UpdateCommentRequest request, Long userId);
}
