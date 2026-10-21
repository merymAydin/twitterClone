package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.Likes;
import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.LikesRepository;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.LikeServiceImpl;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.dto.request.CreateLikeRequest;
import com.example.twitter_challenge.exception.LikeAlreadyExistsException;
import com.example.twitter_challenge.exception.LikeNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceImplTest {

    @Mock
    private LikesRepository likesRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private LikeServiceImpl likeService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void likingOwnTweetDoesNotCreateSelfNotification() {
        User current = user(1L, "alice@example.com");
        Tweet tweet = tweet(10L, current);
        Statistics statistics = statistics(0L);

        authenticateAs(current.getEmail());
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(current.getEmail()))
                .thenReturn(Optional.of(current));
        when(tweetRepository.findVisibleById(10L)).thenReturn(Optional.of(tweet));
        when(likesRepository.findByTweetIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        when(statisticsRepository.findByTweetIdForUpdate(10L)).thenReturn(Optional.of(statistics));

        likeService.createLike(new CreateLikeRequest(10L));

        verify(notificationService, never()).create(any(), any(), any(), any());
        assertEquals(1L, statistics.getLikes());
    }

    @Test
    void likingAnotherUsersTweetNotifiesTheAuthor() {
        User current = user(1L, "alice@example.com");
        User author = user(2L, "bob@example.com");
        Tweet tweet = tweet(10L, author);
        Statistics statistics = statistics(0L);

        authenticateAs(current.getEmail());
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(current.getEmail()))
                .thenReturn(Optional.of(current));
        when(tweetRepository.findVisibleById(10L)).thenReturn(Optional.of(tweet));
        when(likesRepository.findByTweetIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        when(statisticsRepository.findByTweetIdForUpdate(10L)).thenReturn(Optional.of(statistics));

        likeService.createLike(new CreateLikeRequest(10L));

        verify(notificationService).create(1L, 2L, NotificationType.LIKE, "Liked your tweet");
    }

    @Test
    void likingAgainAfterUnlikeReactivatesTheSoftDeletedRow() {
        User current = user(1L, "alice@example.com");
        Tweet tweet = tweet(10L, user(2L, "bob@example.com"));
        Likes existing = new Likes(tweet, current);
        existing.setId(7L);
        existing.setIsDeleted(true);
        Statistics statistics = statistics(3L);

        authenticateAs(current.getEmail());
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(current.getEmail()))
                .thenReturn(Optional.of(current));
        when(tweetRepository.findVisibleById(10L)).thenReturn(Optional.of(tweet));
        when(likesRepository.findByTweetIdAndUserId(10L, 1L)).thenReturn(Optional.of(existing));
        when(statisticsRepository.findByTweetIdForUpdate(10L)).thenReturn(Optional.of(statistics));

        likeService.createLike(new CreateLikeRequest(10L));

        verify(likesRepository).saveAndFlush(existing);
        assertEquals(4L, statistics.getLikes());
    }

    @Test
    void activeDuplicateLikeIsRejected() {
        User current = user(1L, "alice@example.com");
        Tweet tweet = tweet(10L, user(2L, "bob@example.com"));
        Likes existing = new Likes(tweet, current);
        existing.setIsDeleted(false);

        authenticateAs(current.getEmail());
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(current.getEmail()))
                .thenReturn(Optional.of(current));
        when(tweetRepository.findVisibleById(10L)).thenReturn(Optional.of(tweet));
        when(likesRepository.findByTweetIdAndUserId(10L, 1L)).thenReturn(Optional.of(existing));

        assertThrows(
                LikeAlreadyExistsException.class,
                () -> likeService.createLike(new CreateLikeRequest(10L))
        );
    }

    @Test
    void removingAnotherUsersLikeReturnsNotFound() {
        when(likesRepository.findByTweetIdAndUserIdAndIsDeletedFalse(10L, 2L))
                .thenReturn(Optional.empty());

        assertThrows(
                LikeNotFoundException.class,
                () -> likeService.removeLike(new CreateLikeRequest(10L), 2L)
        );
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setIsDeleted(false);
        return user;
    }

    private Tweet tweet(Long id, User user) {
        Tweet tweet = new Tweet();
        tweet.setId(id);
        tweet.setUser(user);
        tweet.setIsDeleted(false);
        return tweet;
    }

    private Statistics statistics(Long likes) {
        Statistics statistics = new Statistics();
        statistics.setLikes(likes);
        statistics.setBookmarks(0L);
        return statistics;
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null)
        );
    }
}
