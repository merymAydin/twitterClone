package com.example.twitter_challenge.controller;


import com.example.twitter_challenge.Service.interfaces.CommentService;


import com.example.twitter_challenge.dto.request.CreateCommentRequest;
import com.example.twitter_challenge.dto.request.UpdateCommentRequest;
import com.example.twitter_challenge.dto.response.CommentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
    public CommentResponse createComment(@Valid @RequestBody CreateCommentRequest request){
        return commentService.createComment(request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeComment(@PathVariable Long id){
        commentService.removeComment(id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping
    public List<CommentResponse> getAllComments(){
        return commentService.findAllComments();
    }
    @GetMapping("/{id}")
    public CommentResponse getComment(@PathVariable Long id){
        return commentService.findCommentById(id);
    }
    @PutMapping("/{id}")
    public CommentResponse updateComment(@PathVariable Long id,@Valid @RequestBody UpdateCommentRequest request){
        return commentService.update(id,request);
    }

}
