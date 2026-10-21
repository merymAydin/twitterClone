package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.MentionNotifier;
import com.example.twitter_challenge.Service.TweetServiceImpl;
import com.example.twitter_challenge.Service.TweetResponseMapper;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.example.twitter_challenge.dto.request.UpdateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TweetServiceImplTest {
    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Tweet tweet;

    @Mock
    private User user;

    @Mock
    private Statistics statistics;

    private TweetServiceImpl tweetService;

    @BeforeEach
    void setUp() {
        // The real mapper is used so the tweet DTO is built exactly like in production. The
        // subscription service is a stub without entitlements, so every author is non premium.
        tweetService = new TweetServiceImpl(
                userRepository,
                tweetRepository,
                statisticsRepository,
                notificationService,
                new TweetResponseMapper(mock(SubscriptionService.class)),
                new MentionNotifier(userRepository, notificationService)
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findTweetById_shouldReturnTweet() {
        when(tweetRepository.findVisibleById(1L)).thenReturn(Optional.of(tweet));
        when(tweet.getContent()).thenReturn("Hello");
        when(tweet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);
        when(tweet.getStatistics()).thenReturn(statistics);

        TweetResponse result = tweetService.findTweetById(1L);

        assertEquals("Hello", result.content());
        assertEquals(1L, result.userId());
    }

    @Test
    void findTweetById_shouldThrowWhenMissing() {
        when(tweetRepository.findVisibleById(99L)).thenReturn(Optional.empty());

        assertThrows(TweetNotFoundException.class, () -> tweetService.findTweetById(99L));
    }

    @Test
    void removeTweet_shouldSoftDeleteAndNotHardDelete() {
        when(tweetRepository.findVisibleById(1L)).thenReturn(Optional.of(tweet));
        when(tweet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);
        when(tweet.getId()).thenReturn(1L);
        when(tweet.getParent()).thenReturn(null);
        when(tweetRepository.findByParentIdAndIsDeletedFalse(1L)).thenReturn(List.of());

        tweetService.removeTweet(1L, 1L);

        verify(tweet).setIsDeleted(true);
        verify(tweetRepository).save(tweet);
        verify(tweetRepository, never()).delete(tweet);
    }

    @Test
    void removeTweet_shouldThrowWhenUserIsNotOwner() {
        when(tweetRepository.findVisibleById(1L)).thenReturn(Optional.of(tweet));
        when(tweet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        assertThrows(ForbiddenException.class, () -> tweetService.removeTweet(1L, 2L));
        verify(tweetRepository, never()).save(tweet);
    }

    @Test
    void findAllTweets_shouldReturnVisibleTweets() {
        Tweet visibleTweet = mock(Tweet.class);
        Page<Tweet> page = new PageImpl<>(List.of(visibleTweet));
        when(tweetRepository.findVisible(anyPageable())).thenReturn(page);
        when(visibleTweet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);
        when(visibleTweet.getStatistics()).thenReturn(statistics);

        Page<TweetResponse> result = tweetService.findAllTweets(page.getPageable());

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void update_shouldUpdateOwnedTweet() {
        when(tweetRepository.findVisibleById(1L)).thenReturn(Optional.of(tweet));
        when(tweet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);
        when(tweet.getContent()).thenReturn("old");
        when(tweet.getStatistics()).thenReturn(statistics);

        tweetService.update(1L, new UpdateTweetRequest("Updated content"), 1L);

        verify(tweet).setContent("Updated content");
        verify(tweetRepository).save(tweet);
    }

    @Test
    void update_shouldRejectAnotherUsersTweet() {
        when(tweetRepository.findVisibleById(1L)).thenReturn(Optional.of(tweet));
        when(tweet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        assertThrows(
                ForbiddenException.class,
                () -> tweetService.update(1L, new UpdateTweetRequest("hijacked"), 2L)
        );
        verify(tweetRepository, never()).save(tweet);
    }

    @Test
    void retweet_shouldCreateRetweetAndIncrementCounter() {
        when(tweetRepository.findVisibleById(1L)).thenReturn(Optional.of(tweet));
        when(tweet.getId()).thenReturn(1L);
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(tweetRepository.findByParentIdAndUserIdAndContentIsNull(1L, 1L))
                .thenReturn(Optional.empty());
        when(tweetRepository.save(org.mockito.ArgumentMatchers.any(Tweet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(statisticsRepository.findByTweetIdForUpdate(1L)).thenReturn(Optional.of(statistics));
        when(statistics.getRetweets()).thenReturn(2L);

        tweetService.retweet(1L, 1L);

        verify(statistics).setRetweets(3L);
        verify(statisticsRepository).save(statistics);
        verify(tweetRepository).save(org.mockito.ArgumentMatchers.any(Tweet.class));
    }

    @Test
    void unretweet_shouldSoftDeleteRetweetAndDecrementCounter() {
        when(tweetRepository.findByParentIdAndUserIdAndContentIsNullAndIsDeletedFalse(1L, 1L))
                .thenReturn(Optional.of(tweet));
        when(statisticsRepository.findByTweetIdForUpdate(1L)).thenReturn(Optional.of(statistics));
        when(statistics.getRetweets()).thenReturn(1L);

        tweetService.unretweet(1L, 1L);

        verify(statistics).setRetweets(0L);
        verify(tweet).setIsDeleted(true);
        verify(tweetRepository).save(tweet);
    }

    @Test
    void quoteTweet_shouldCreateQuoteAndIncrementCounter() {
        when(tweetRepository.findVisibleById(1L)).thenReturn(Optional.of(tweet));
        when(tweet.getId()).thenReturn(1L);
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(user));
        when(tweetRepository.save(org.mockito.ArgumentMatchers.any(Tweet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(statisticsRepository.save(org.mockito.ArgumentMatchers.any(Statistics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(statisticsRepository.findByTweetIdForUpdate(1L)).thenReturn(Optional.of(statistics));
        when(statistics.getQuotes()).thenReturn(0L);

        tweetService.quoteTweet(1L, 2L, "quoted");

        verify(statistics).setQuotes(1L);
    }

    @Test
    void findAllTweetsByUserId_shouldUseVisibleQuery() {
        Tweet visibleTweet = mock(Tweet.class);
        Page<Tweet> page = new PageImpl<>(List.of(visibleTweet));
        when(tweetRepository.findVisibleByUserId(1L, Pageable.unpaged())).thenReturn(page);

        Page<Tweet> result = tweetService.findAllTweetsByUserId(1L, Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
    }

    private static Pageable anyPageable() {
        return org.mockito.ArgumentMatchers.any(Pageable.class);
    }
}
