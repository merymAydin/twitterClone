package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Likes;
import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.LikesRepository;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.LikeService;
import com.example.twitter_challenge.dto.request.CreateLikeRequest;
import com.example.twitter_challenge.dto.response.LikeResponse;
import com.example.twitter_challenge.exception.*;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LikeServiceImpl implements LikeService {
    private final LikesRepository likesRepository;
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    private final StatisticsRepository statisticsRepository;

    public LikeServiceImpl(LikesRepository likesRepository, UserRepository userRepository, TweetRepository tweetRepository, StatisticsRepository statisticsRepository) {
        this.likesRepository = likesRepository;
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
        this.statisticsRepository = statisticsRepository;
    }

    @Transactional
    @Override
    public LikeResponse createLike(CreateLikeRequest request) {
        Optional<Likes> existingLike =
                likesRepository.findByTweetIdAndUserId(
                        request.tweetId(),
                        request.userId()
                );
        if (existingLike.isPresent()) {
            throw new LikeAlreadyExistsException(
                    "User " + request.userId() + " has already liked tweet " + request.tweetId()
            );
        }

        Tweet tweet = tweetRepository.findById(request.tweetId()).orElseThrow(() -> new TweetNotFoundException("Tweet with" + request.tweetId() + "not found"));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() ->
                        new UserNotFoundException("User with " + request.userId() + " not found")
                );
        String curName = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!curName.equals(user.getUserName())){
            throw new ForbiddenException("you are not allowed to like this tweet");
        }

        Likes likes = new Likes(tweet, user);


        Likes savedLike = likesRepository.save(likes);
        Statistics statistics = statisticsRepository
                .findByTweetId(savedLike.getTweet().getId())
                .orElseThrow(() -> new StatisticsNotFoundException(
                        "Statistics for tweet " + savedLike.getTweet().getId() + " not found"
                ));
        statistics.setLikes(statistics.getLikes() + 1);
        statisticsRepository.save(statistics);

        return new LikeResponse(savedLike.getUser().getId(), savedLike.getTweet().getId());

    }
    @Transactional
    @Override
    public void removeLike(CreateLikeRequest request) {
         Likes like = likesRepository.findByTweetIdAndUserId(request.tweetId(), request.userId() ).orElseThrow(() ->
                new LikeNotFoundException("Like for tweet " + request.tweetId() + " not found")
        );
         String curUsername = SecurityContextHolder.getContext().getAuthentication().getName();
         if(!curUsername.equals(like.getUser().getUserName())){
             throw new  ForbiddenException("You are not allowed to remove this like");
         }
        Statistics statistics =statisticsRepository.findByTweetId(like.getTweet().getId()).orElseThrow(() ->new StatisticsNotFoundException("Statistics for tweet " + like.getTweet().getId() + " not found"));

        statistics.setLikes(statistics.getLikes() - 1);
        statisticsRepository.save(statistics);
        likesRepository.delete(like);

    }

    @Override
    public LikeResponse findById(Long id) {
        Likes likes = likesRepository.findById(id).orElseThrow(() -> new LikeNotFoundException("Like with " + id + " not found"));
        return new LikeResponse(likes.getUser().getId(), likes.getTweet().getId());
    }

    @Override
    public List<LikeResponse> findAll() {
        return likesRepository.findAll()
                .stream()
                .map(likes -> new LikeResponse(
                        likes.getUser().getId(),
                        likes.getTweet().getId()
                ))
                .toList();
    }

}
