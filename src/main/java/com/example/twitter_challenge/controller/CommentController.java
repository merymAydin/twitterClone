package com.example.twitter_challenge.controller;


import com.example.twitter_challenge.Service.interfaces.CommentService;


import com.example.twitter_challenge.dto.request.CreateCommentRequest;
import com.example.twitter_challenge.dto.response.CommentResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    @DeleteMapping("/{id}")
    public void removeComment(@PathVariable Long id){
        commentService.removeComment(id);
    }
    @GetMapping
    public List<CommentResponse> getAllComments(){
        return commentService.findAllComments();
    }
    @GetMapping("/{id}")
    public CommentResponse getComment(@PathVariable Long id){
        return commentService.findCommentById(id);
    }

}
