package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.TweetService;
import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.request.UpdateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import com.example.twitter_challenge.exception.StatisticsNotFoundException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.naming.AuthenticationException;
import java.util.List;

@Service
public class TweetServiceImpl implements TweetService {
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    private final StatisticsRepository statisticsRepository;

    public TweetServiceImpl(UserRepository userRepository, TweetRepository tweetRepository, StatisticsRepository statisticsRepository) {
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
        this.statisticsRepository = statisticsRepository;
    }
    @Override
    public TweetResponse createTweet(CreateTweetRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() ->
                        new UserNotFoundException("User with " + request.userId() + " not found")
                );
        String curName = SecurityContextHolder.getContext().getAuthentication().getName();
        if(!curName.equals(user.getUserName())){
            throw new ForbiddenException("You are not allowed to create this tweet");
        }

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
        Statistics statistics =new Statistics();
        statistics.setTweet(savedTweet);
        statistics.setViews(0L);
        statistics.setRetweets(0L);
        statistics.setBookmarks(0L);
        statistics.setComments(0L);
        statistics.setLikes(0L);
        statisticsRepository.save(statistics);
        return new TweetResponse(savedTweet.getContent(), savedTweet.getUser().getId(), savedTweet.getLocation(),
                savedTweet.getParent() != null ? savedTweet.getParent().getId() : null);
    }

    @Transactional
    @Override
    public void removeTweet(Long id) {
        Tweet tweet = tweetRepository.findById(id).orElseThrow(()->new TweetNotFoundException("Tweet with " + id + " not found"));
        String currentUserName = SecurityContextHolder.getContext().getAuthentication().getName();
        if(!currentUserName.equals(tweet.getUser().getUserName())){
            throw new  ForbiddenException("You are not allowed to remove this tweet");
        }
        Statistics statistics = tweet.getStatistics();
        statisticsRepository.delete(statistics);
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
        String currentUserName = SecurityContextHolder.getContext().getAuthentication().getName();
        if(!currentUserName.equals(tweet.getUser().getUserName())){
            throw new  ForbiddenException("You are not allowed to update this tweet");
        }
        tweet.setContent(request.content());
        tweetRepository.save(tweet);
        return new TweetResponse(tweet.getContent(), tweet.getId(),  tweet.getLocation(), tweet.getParent() != null ? tweet.getParent().getId() : null);
    }

}

