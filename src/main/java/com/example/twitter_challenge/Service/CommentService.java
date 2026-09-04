package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.dto.request.CreateCommentRequest;
import com.example.twitter_challenge.dto.response.CommentResponse;

public interface CommentService {
    CommentResponse createComment(CreateCommentRequest request);
}
