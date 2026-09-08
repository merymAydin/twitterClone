package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.TweetService;
import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.request.UpdateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

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

        Tweet parentTweet =null;
        if (request.parentId() != null) {
            parentTweet = tweetRepository.findById(request.parentId())
                    .orElseThrow(() ->
                            new TweetNotFoundException(
                                    "Tweet with " + request.parentId() + " not found"
                            )
                    );
        }
        Tweet tweet = new Tweet();
        tweet.setContent(request.content());
        tweet.setUser(user);
        tweet.setLocation(request.location());
        tweet.setParent(parentTweet);
        Tweet savedTweet = tweetRepository.save(tweet);
        return new TweetResponse(savedTweet.getContent(), savedTweet.getUser().getId(), savedTweet.getLocation(),
                savedTweet.getParent() != null ? savedTweet.getParent().getId() : null);
    }

    @Override
    public void removeTweet(Long id) {
        Tweet tweet = tweetRepository.findById(id).orElseThrow(()->new TweetNotFoundException("Tweet with " + id + " not found"));
        tweetRepository.delete(tweet);
    }

    @Override
    public List<TweetResponse> findAllTweets() {
        return tweetRepository.findAll()
                .stream()
                .map(tweet ->  new TweetResponse(
                        tweet.getContent(),
                        tweet.getUser().getId(),
                        tweet.getLocation(),
                        tweet.getParent() != null ? tweet.getParent().getId() : null
                ))
                .toList();
    }

    @Override
    public TweetResponse findTweetById(Long id) {
        Tweet tweet = tweetRepository.findById(id).orElseThrow(()->new TweetNotFoundException("Tweet with " + id + " not found"));
        return new TweetResponse(tweet.getContent(),tweet.getUser().getId(),tweet.getLocation(),tweet.getParent() != null ? tweet.getParent().getId() : null);
    }

    @Override
    public TweetResponse update(Long id, UpdateTweetRequest request) {
        Tweet tweet = tweetRepository.findById(id).orElseThrow(()->new TweetNotFoundException("Tweet with " + id + " not found"));
        tweet.setContent(request.content());
        tweetRepository.save(tweet);
        return new TweetResponse(tweet.getContent(), tweet.getId(),  tweet.getLocation(), tweet.getParent() != null ? tweet.getParent().getId() : null);
    }

}

