package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Likes;
import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.LikesRepository;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.LikeService;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.dto.request.CreateLikeRequest;
import com.example.twitter_challenge.dto.response.LikeResponse;
import com.example.twitter_challenge.exception.LikeAlreadyExistsException;
import com.example.twitter_challenge.exception.LikeNotFoundException;
import com.example.twitter_challenge.exception.StatisticsNotFoundException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LikeServiceImpl implements LikeService {
    private final LikesRepository likesRepository;
    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    private final StatisticsRepository statisticsRepository;
    private final NotificationService notificationService;

    public LikeServiceImpl(
            LikesRepository likesRepository,
            UserRepository userRepository,
            TweetRepository tweetRepository,
            StatisticsRepository statisticsRepository,
            NotificationService notificationService
    ) {
        this.likesRepository = likesRepository;
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
        this.statisticsRepository = statisticsRepository;
        this.notificationService = notificationService;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private void adjustLikes(Long tweetId, long delta) {
        Statistics statistics = statisticsRepository.findByTweetIdForUpdate(tweetId)
                .orElseThrow(() -> new StatisticsNotFoundException(
                        "Statistics for tweet " + tweetId + " not found"
                ));
        statistics.setLikes(Math.max(0L, (statistics.getLikes() == null ? 0L : statistics.getLikes()) + delta));
        statisticsRepository.save(statistics);
    }

    @Transactional
    @Override
    public LikeResponse createLike(CreateLikeRequest request) {
        User user = currentUser();
        Tweet tweet = tweetRepository.findVisibleById(request.tweetId())
                .orElseThrow(() -> new TweetNotFoundException(
                        "Tweet with " + request.tweetId() + " not found"
                ));

        Optional<Likes> existing =
                likesRepository.findByTweetIdAndUserId(request.tweetId(), user.getId());
        Likes like;
        if (existing.isPresent()) {
            like = existing.get();
            if (!Boolean.TRUE.equals(like.getIsDeleted())) {
                throw new LikeAlreadyExistsException(
                        "User " + user.getId() + " has already liked tweet " + request.tweetId()
                );
            }
            // The (tweet_id, user_id) unique constraint also covers soft-deleted likes.
            like.setIsDeleted(false);
        } else {
            like = new Likes(tweet, user);
        }

        try {
            likesRepository.saveAndFlush(like);
        } catch (DataIntegrityViolationException exception) {
            // Two concurrent requests can both pass the pre-check. The database unique constraint
            // is the final authority and maps to the documented 409 response.
            throw new LikeAlreadyExistsException(
                    "User " + user.getId() + " has already liked tweet " + request.tweetId()
            );
        }

        adjustLikes(tweet.getId(), 1);
        if (!tweet.getUser().getId().equals(user.getId())) {
            notificationService.create(
                    user.getId(),
                    tweet.getUser().getId(),
                    NotificationType.LIKE,
                    "Liked your tweet"
            );
        }
        return new LikeResponse(user.getId(), tweet.getId());
    }

    @Transactional
    @Override
    public void removeLike(CreateLikeRequest request, Long userId) {
        Likes like = likesRepository
                .findByTweetIdAndUserIdAndIsDeletedFalse(request.tweetId(), userId)
                .orElseThrow(() -> new LikeNotFoundException(
                        "Like for tweet " + request.tweetId() + " not found"
                ));
        if (!like.getUser().getId().equals(userId)) {
            throw new LikeNotFoundException("Like for tweet " + request.tweetId() + " not found");
        }

        adjustLikes(like.getTweet().getId(), -1);
        like.setIsDeleted(true);
        likesRepository.save(like);
    }

    @Transactional(readOnly = true)
    @Override
    public LikeResponse findById(Long id) {
        Likes like = likesRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new LikeNotFoundException("Like with " + id + " not found"));
        return new LikeResponse(like.getUser().getId(), like.getTweet().getId());
    }

    @Transactional(readOnly = true)
    @Override
    public List<LikeResponse> findAll() {
        return likesRepository.findAll().stream()
                .filter(like -> !Boolean.TRUE.equals(like.getIsDeleted()))
                .map(like -> new LikeResponse(like.getUser().getId(), like.getTweet().getId()))
                .toList();
    }
}
