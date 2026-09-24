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
import com.example.twitter_challenge.dto.response.StatisticsResponse;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import com.example.twitter_challenge.exception.StatisticsNotFoundException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;


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

    private Statistics createStatistics(Tweet tweet) {
        Statistics statistics =new Statistics();
        statistics.setViews(0L);
        statistics.setRetweets(0L);
        statistics.setBookmarks(0L);
        statistics.setComments(0L);
        statistics.setLikes(0L);
        statistics.setQuotes(0L);
        statistics.setTweet(tweet);
        return statistics;
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
    public TweetResponse createTweet(CreateTweetRequest request) {

        String curName = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUserName(curName)
                .orElseThrow(() ->
                        new UserNotFoundException("User with username " + curName + " not found")
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

        Statistics statistics = createStatistics(savedTweet);

        StatisticsResponse statisticsResponse = toStatisticsResponse(statistics);


        statisticsRepository.save(statistics);
        return new TweetResponse(savedTweet.getContent(), savedTweet.getUser().getId(), savedTweet.getLocation(),
                savedTweet.getParent() != null ? savedTweet.getParent().getId() : null,statisticsResponse);
    }


    @Transactional
    @Override
    public void removeTweet(Long id,Long userId) {
        Tweet tweet = tweetRepository.findById(id).orElseThrow(()->new TweetNotFoundException("Tweet with " + id + " not found"));

        if(!tweet.getUser().getId().equals(userId)){
            throw new  ForbiddenException("You are not allowed to remove this tweet");
        }
        Statistics statistics = tweet.getStatistics();
        statisticsRepository.delete(statistics);
        tweetRepository.delete(tweet);
    }

    @Override
    public Page<TweetResponse> findAllTweets(Pageable pageable) {

        return tweetRepository.findAll(pageable)
                .map(tweet -> new TweetResponse(
                        tweet.getContent(),
                        tweet.getUser().getId(),
                        tweet.getLocation(),
                        tweet.getParent() != null ? tweet.getParent().getId() : null,
                        toStatisticsResponse(tweet.getStatistics())
                ));
    }

    @Override
    public TweetResponse findTweetById(Long id) {
        Tweet tweet = tweetRepository.findById(id).orElseThrow(()->new TweetNotFoundException("Tweet with " + id + " not found"));
        return new TweetResponse(
                tweet.getContent(),
                tweet.getUser().getId(),
                tweet.getLocation(),
                tweet.getParent() != null ? tweet.getParent().getId() : null,
                toStatisticsResponse(tweet.getStatistics()));
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
        return new TweetResponse(tweet.getContent(), tweet.getId(),  tweet.getLocation(), tweet.getParent() != null ? tweet.getParent().getId() : null,toStatisticsResponse(tweet.getStatistics()));
    }

    @Override
    public Page<TweetResponse> findByContentContaining(String keyword, Pageable pageable) {

        Page<Tweet> tweets = tweetRepository.findByContentContaining(keyword,pageable);
        return tweets.map(tweet -> new TweetResponse(tweet.getContent(), tweet.getId(),tweet.getLocation(), tweet.getParent() != null ? tweet.getParent().getId() : null,toStatisticsResponse(tweet.getStatistics())));
    }

    @Transactional
    @Override
    public TweetResponse retweet(Long originalTweetId, Long currentUserId) {

        Tweet tweet = tweetRepository.findById(originalTweetId).orElseThrow(()->new TweetNotFoundException("Tweet with " + originalTweetId+ " not found"));
        User user = userRepository.findById(currentUserId)
                .orElseThrow(()->new UserNotFoundException("User with " + currentUserId+ " not found"));

        Optional<Tweet> rettw = tweetRepository.findByParentIdAndUserId(originalTweetId,currentUserId);
        if(rettw.isPresent()){
            throw new  ForbiddenException("You already retweeted this tweet");
        }

        Tweet retweetedTweet = new Tweet();
        retweetedTweet.setParent(tweet);
        retweetedTweet.setUser(user);

        Statistics statistics = tweet.getStatistics();
        statistics.setRetweets(statistics.getRetweets() + 1);
        statisticsRepository.save(statistics);


        tweetRepository.save(retweetedTweet);
        return new TweetResponse(tweet.getContent(),retweetedTweet.getId(),retweetedTweet.getLocation(), retweetedTweet.getParent() != null ? retweetedTweet.getParent().getId() : null,toStatisticsResponse(tweet.getStatistics()));

    }

    @Transactional
    @Override
    public void unretweet(Long originalTweetId, Long currentUserId) {
        Tweet tweet = tweetRepository.findById(originalTweetId).orElseThrow(()->new TweetNotFoundException("Retweet not found for user " + currentUserId));


        Tweet retweet = tweetRepository.findByParentIdAndUserId(originalTweetId,currentUserId).orElseThrow(()->new TweetNotFoundException("Tweet with " + currentUserId+ " not found"));



        Statistics statistics = statisticsRepository.findByTweetId(tweet.getId()).orElseThrow(() ->
                new StatisticsNotFoundException(
                        "Statistics for tweet " + tweet.getId() + " not found"
                )
        );
        statistics.setRetweets(statistics.getRetweets() - 1);
        statisticsRepository.save(statistics);

        tweetRepository.delete(retweet);
    }
    @Transactional
    @Override
    public TweetResponse quoteTweet(Long originalTweetId, Long currentUserId, String content) {
        Tweet tweet = tweetRepository.findById(originalTweetId).orElseThrow(()->new TweetNotFoundException("Tweet with " + originalTweetId+ " not found"));
        User user = userRepository.findById(currentUserId)
                .orElseThrow(()->new UserNotFoundException("User with " + currentUserId+ " not found"));
        Tweet quoteTweet = new Tweet();
        quoteTweet.setParent(tweet);
        quoteTweet.setUser(user);
        quoteTweet.setContent(content);
        Statistics statistics = statisticsRepository.findByTweetId(originalTweetId).orElseThrow(() ->
                new StatisticsNotFoundException(
                        "Statistics for tweet " + tweet.getId() + " not found"
                )
        );
        statistics.setQuotes(statistics.getQuotes() + 1);
        statisticsRepository.save(statistics);


        Statistics quoteStatistics = createStatistics(quoteTweet);
        tweetRepository.save(quoteTweet);
        statisticsRepository.save(quoteStatistics);

        StatisticsResponse statisticsResponse = toStatisticsResponse(quoteStatistics);
        return new TweetResponse(quoteTweet.getContent(), quoteTweet.getId(), quoteTweet.getLocation(), quoteTweet.getParent() != null ? quoteTweet.getParent().getId() : null,statisticsResponse);
    }

    @Transactional
    @Override
    public void removeQuote(Long quoteTweetId, Long currentUserId) {

        Tweet quote = tweetRepository.findById(quoteTweetId).orElseThrow(()->new TweetNotFoundException("Tweet with " + quoteTweetId+ " not found"));
        Tweet originalTweet = quote.getParent();
        if(!quote.getUser().getId().equals(currentUserId)) {
            throw new ForbiddenException("User with " + quote.getUser().getId() + " not allowed to remove this quote");
        }
        Statistics statistics = statisticsRepository.findByTweetId(originalTweet.getId()).orElseThrow(()->new StatisticsNotFoundException(
                "Statistics for tweet " + originalTweet.getId() + " not found"
        ));


        statistics.setQuotes(statistics.getQuotes() - 1);


        Statistics quoteStatistics = statisticsRepository
                .findByTweetId(quoteTweetId)
                .orElseThrow(() -> new StatisticsNotFoundException(
                        "Statistics for tweet " + quoteTweetId + " not found"
                ));


        statisticsRepository.delete(quoteStatistics);


        tweetRepository.delete(quote);

    }


}

