package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TweetServiceImpl implements TweetService {
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;

    public TweetServiceImpl(UserRepository userRepository, TweetRepository tweetRepository) {
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
    }
    @Override
    public TweetResponse createTweet(CreateTweetRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() ->
                        new UserNotFoundException("User with " + request.userId() + " not found")
                );
        Tweet tweet = new Tweet();
        tweet.setContent(request.content());
        tweet.setUser(user);
        tweet.setLocation(request.location());
        Tweet savedTweet = tweetRepository.save(tweet);
        return new TweetResponse(savedTweet.getContent(), savedTweet.getUser().getId(), savedTweet.getLocation());
    }

}
