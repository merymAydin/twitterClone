package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Follower;
import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Repository.FollowerRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Service.interfaces.FeedService;
import com.example.twitter_challenge.dto.response.StatisticsResponse;
import com.example.twitter_challenge.dto.response.TweetResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FeedServiceImpl implements FeedService {
    private final TweetRepository tweetRepository;
    private final FollowerRepository followerRepository;

    public FeedServiceImpl(TweetRepository tweetRepository, FollowerRepository followerRepository) {
        this.tweetRepository = tweetRepository;
        this.followerRepository = followerRepository;
    }

    private StatisticsResponse toStatisticsResponse(Statistics statistics) {
        return new StatisticsResponse(
                statistics.getTweet().getId(),
                statistics.getViews(),
                statistics.getLikes(),
                statistics.getComments(),
                statistics.getBookmarks(),
                statistics.getRetweets(),
                statistics.getQuotes()
        );
    }


    @Override
    public Page<TweetResponse> getTweets(Long userId, Pageable pageable) {
        List<Follower> followers = followerRepository.findByFollowerId(userId);
        List<Long> followingIds = followers.stream().map(follower -> follower.getFollowing().getId()).toList();
        return tweetRepository.findByUserIdIn(followingIds, pageable)
                .map(tweet -> new TweetResponse(
                        tweet.getContent(),
                        tweet.getUser().getId(),
                        tweet.getLocation(),
                        tweet.getParent() != null ? tweet.getParent().getId() : null,
                        toStatisticsResponse(tweet.getStatistics())
                ));
    }
}
