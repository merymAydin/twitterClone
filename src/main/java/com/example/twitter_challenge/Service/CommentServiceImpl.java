package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Comment;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.CommentRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.CommentService;
import com.example.twitter_challenge.dto.request.CreateCommentRequest;
import com.example.twitter_challenge.dto.request.UpdateCommentRequest;
import com.example.twitter_challenge.dto.response.CommentResponse;
import com.example.twitter_challenge.exception.CommentNotFound;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentServiceImpl implements CommentService {
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    public  CommentServiceImpl(CommentRepository commentRepository, UserRepository userRepository, TweetRepository tweetRepository) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
    }
    @Override
    public CommentResponse createComment(CreateCommentRequest request) {

        User user = userRepository.findById(request.userId())
                .orElseThrow(() ->
                        new UserNotFoundException("User with " + request.userId() + " not found")
                );
        Tweet tweet = tweetRepository.findById(request.tweetId()).orElseThrow(()-> new TweetNotFoundException("Tweet with" + request.tweetId()+ "not found"));
        Comment comment = new Comment(tweet,user,request.content());
        Comment savedComment = commentRepository.save(comment);
        return new CommentResponse(savedComment.getUser().getId(),savedComment.getTweet().getId(),savedComment.getContent());

    }

    @Override
    public void removeComment(Long id) {
        Comment comment = commentRepository.findById(id).orElseThrow(()-> new TweetNotFoundException("Tweet with" + id + "not found"));
        commentRepository.delete(comment);
    }

    @Override
    public List<CommentResponse> findAllComments() {
        return commentRepository.findAll()
                .stream()
                .map(comment -> new CommentResponse(
                        comment.getId(),
                        comment.getTweet().getId(),
                        comment.getContent()
                ))
                .toList();
    }

    @Override
    public CommentResponse findCommentById(Long id) {
        Comment comment = commentRepository.findById(id).orElseThrow(()-> new CommentNotFound("Comment " + id + "Not Found"));
        return new CommentResponse(comment.getUser().getId(),comment.getTweet().getId(),comment.getContent());
    }

    @Override
    public CommentResponse update(Long twitterId, UpdateCommentRequest request) {
        Comment comment = commentRepository.findById(twitterId).orElseThrow(()-> new CommentNotFound("Comment " + twitterId + "Not Found"));
        comment.setContent(request.content());
        commentRepository.save(comment);
        return new CommentResponse(comment.getUser().getId(),comment.getTweet().getId(),comment.getContent());
    }
}

