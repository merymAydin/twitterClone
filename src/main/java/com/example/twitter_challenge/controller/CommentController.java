package com.example.twitter_challenge.controller;


import com.example.twitter_challenge.Service.CommentService;

import com.example.twitter_challenge.dto.request.CreateCommentRequest;
import com.example.twitter_challenge.dto.response.CommentResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comments")
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }
    @PostMapping
    public CommentResponse createComment(@RequestBody CreateCommentRequest request){
        return commentService.createComment(request);
    }
}
