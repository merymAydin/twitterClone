package com.example.twitter_challenge.controller;


import com.example.twitter_challenge.Service.interfaces.CommentService;


import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.request.CreateCommentRequest;
import com.example.twitter_challenge.dto.request.UpdateCommentRequest;
import com.example.twitter_challenge.dto.response.CommentResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comments")
public class CommentController {
    private final CommentService commentService;
    private final UserService userService;
    public CommentController(CommentService commentService, UserService userService) {
        this.commentService = commentService;
        this.userService = userService;
    }

    @PostMapping
    public CommentResponse createComment(@Valid @RequestBody CreateCommentRequest request){
        return commentService.createComment(request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeComment(@PathVariable Long id){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (String) authentication.getPrincipal();
        UserResponse user = userService.findByUserName(username);
        commentService.removeComment(id,user.userId());
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (String) authentication.getPrincipal();
        UserResponse user = userService.findByUserName(username);
        return commentService.update(id,request,user.userId());
    }

}
