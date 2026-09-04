package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Likes;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.LikesRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.dto.request.CreateLikeRequest;
import com.example.twitter_challenge.dto.response.LikeResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LikeServiceImpl implements LikeService{
    private final LikesRepository likesRepository;
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    public LikeServiceImpl(LikesRepository likesRepository, UserRepository userRepository, TweetRepository tweetRepository) {
        this.likesRepository = likesRepository;
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
    }
    @Override
    public Optional<Likes> findByTweetIdAndUserId(Long tweetId, Long userId) {
        return likesRepository.findByTweetIdAndUserId(tweetId, userId);
    }

    @Override
    public LikeResponse createLike(CreateLikeRequest request) {
        Optional<Likes> existingLike =
                likesRepository.findByTweetIdAndUserId(
                        request.tweetId(),
                        request.userId()
                );
        if (existingLike.isPresent()) {
            throw new RuntimeException("Like already exists");
        }
        Tweet tweet = tweetRepository.findById(request.tweetId()).orElseThrow();
        User user = userRepository.findById(request.userId()).orElseThrow();

        Likes likes = new Likes(tweet, user);
        Likes savedLike = likesRepository.save(likes);
        return new LikeResponse(savedLike.getUser().getId(), savedLike.getTweet().getId());

    }
}
