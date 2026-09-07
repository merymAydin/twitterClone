package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Likes;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.LikesRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.LikeService;
import com.example.twitter_challenge.dto.request.CreateLikeRequest;
import com.example.twitter_challenge.dto.response.LikeResponse;
import com.example.twitter_challenge.exception.LikeAlreadyExistsException;
import com.example.twitter_challenge.exception.LikeNotFoundException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LikeServiceImpl implements LikeService {
    private final LikesRepository likesRepository;
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;

    public LikeServiceImpl(LikesRepository likesRepository, UserRepository userRepository, TweetRepository tweetRepository) {
        this.likesRepository = likesRepository;
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
    }

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

        Likes likes = new Likes(tweet, user);
        Likes savedLike = likesRepository.save(likes);
        return new LikeResponse(savedLike.getUser().getId(), savedLike.getTweet().getId());

    }

    @Override
    public void removeLike(CreateLikeRequest request) {
         Likes like = likesRepository.findByTweetIdAndUserId(request.tweetId(), request.userId() ).orElseThrow(() ->
                new LikeNotFoundException("Like with " + request.tweetId() + " not found")
        );
         likesRepository.delete(like);

    }

}
