package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.*;
import com.example.twitter_challenge.Repository.CommentRepository;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.CommentService;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.dto.request.CreateCommentRequest;
import com.example.twitter_challenge.dto.request.UpdateCommentRequest;
import com.example.twitter_challenge.dto.response.CommentResponse;
import com.example.twitter_challenge.exception.*;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentServiceImpl implements CommentService {
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    private final StatisticsRepository statisticsRepository;
    private final NotificationService notificationService;

    public  CommentServiceImpl(CommentRepository commentRepository, UserRepository userRepository, TweetRepository tweetRepository, StatisticsRepository statisticsRepository, NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
        this.statisticsRepository = statisticsRepository;
        this.notificationService = notificationService;
    }
    @Transactional
    @Override
    public CommentResponse createComment(CreateCommentRequest request) {


        String curName = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUserName(curName)
                .orElseThrow(() ->
                        new UserNotFoundException("User with " + curName + " name not found")
                );

        Tweet tweet = tweetRepository.findById(request.tweetId()).orElseThrow(()-> new TweetNotFoundException("Tweet with" + request.tweetId()+ "not found"));
        Comment comment = new Comment(tweet,user,request.content());
        Comment savedComment = commentRepository.save(comment);
        Statistics statistics = statisticsRepository.findByTweetId(savedComment.getTweet().getId()).orElseThrow(()->new StatisticsNotFoundException("Statistics for tweet " + savedComment.getTweet().getId() + " not found"));
        statistics.setComments(statistics.getComments() + 1);
        statisticsRepository.save(statistics);
        notificationService.create(savedComment.getUser().getId(),savedComment.getId(), NotificationType.COMMENT,"Commented on your post");
        return new CommentResponse(savedComment.getId(), savedComment.getTweet().getUser().getId(),savedComment.getTweet().getId(),savedComment.getContent());

    }

    @Transactional
    @Override
    public void removeComment(Long id,Long userId) {
        Comment comment = commentRepository.findById(id).orElseThrow(()-> new CommentNotFound("Comment with" + id + "not found"));
        if(!comment.getUser().getId().equals(userId)){
            throw new ForbiddenException("You are not allowed to remove this comment");
        }
        Statistics statistics = statisticsRepository.findByTweetId(comment.getTweet().getId()).orElseThrow(()->new StatisticsNotFoundException("Statistics for tweet " + comment.getTweet().getId() + " not found"));
        statistics.setComments(statistics.getComments()-1);
        statisticsRepository.save(statistics);
        commentRepository.delete(comment);
    }

    @Override
    public List<CommentResponse> findAllComments() {
        return commentRepository.findAll()
                .stream()
                .map(comment -> new CommentResponse(
                        comment.getId(),
                        comment.getUser().getId(),
                        comment.getTweet().getId(),
                        comment.getContent()
                ))
                .toList();
    }

    @Override
    public CommentResponse findCommentById(Long id) {
        Comment comment = commentRepository.findById(id).orElseThrow(()-> new CommentNotFound("Comment " + id + "Not Found"));
        return new CommentResponse(comment.getId(),comment.getUser().getId(),comment.getTweet().getId(),comment.getContent());
    }

    @Override
    public CommentResponse update(Long commentId, UpdateCommentRequest request,Long userId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(()-> new CommentNotFound("Comment " + commentId + "Not Found"));

        if(!comment.getUser().getId().equals(userId)){
            throw new ForbiddenException("You are not allowed to update this comment");
        }
        comment.setContent(request.content());
        commentRepository.save(comment);
        return new CommentResponse(comment.getId(),comment.getUser().getId(),comment.getTweet().getId(),comment.getContent());
    }
}

