package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Comment;
import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.CommentRepository;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.CommentService;
import com.example.twitter_challenge.dto.request.CreateCommentRequest;
import com.example.twitter_challenge.dto.request.UpdateCommentRequest;
import com.example.twitter_challenge.dto.response.CommentResponse;
import com.example.twitter_challenge.exception.CommentNotFound;
import com.example.twitter_challenge.exception.StatisticsNotFoundException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentServiceImpl implements CommentService {
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    private final StatisticsRepository statisticsRepository;

    public  CommentServiceImpl(CommentRepository commentRepository, UserRepository userRepository, TweetRepository tweetRepository, StatisticsRepository statisticsRepository) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
        this.statisticsRepository = statisticsRepository;
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
        Statistics statistics = statisticsRepository.findByTweetId(savedComment.getTweet().getId()).orElseThrow(()->new StatisticsNotFoundException("Statistics for tweet " + savedComment.getTweet().getId() + " not found"));
        statistics.setComments(statistics.getComments() + 1);
        statisticsRepository.save(statistics);
        return new CommentResponse(savedComment.getId(), savedComment.getUser().getId(),savedComment.getTweet().getId(),savedComment.getContent());

    }

    @Override
    public void removeComment(Long id) {
        Comment comment = commentRepository.findById(id).orElseThrow(()-> new TweetNotFoundException("Tweet with" + id + "not found"));
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
    public CommentResponse update(Long twitterId, UpdateCommentRequest request) {
        Comment comment = commentRepository.findById(twitterId).orElseThrow(()-> new CommentNotFound("Comment " + twitterId + "Not Found"));
        comment.setContent(request.content());
        commentRepository.save(comment);
        return new CommentResponse(comment.getId(),comment.getUser().getId(),comment.getTweet().getId(),comment.getContent());
    }
}

