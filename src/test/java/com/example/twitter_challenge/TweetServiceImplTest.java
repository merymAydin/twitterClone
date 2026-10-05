package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.TweetServiceImpl;
import com.example.twitter_challenge.dto.request.UpdateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
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

import static java.awt.SystemColor.text;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TweetServiceImplTest {
    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StatisticsRepository statisticsRepository;
    @Mock
    private Tweet tweet;
    @Mock
    private User user;
    @Mock
    private Statistics statistics;

    @InjectMocks
    private TweetServiceImpl tweetServiceImpl;

    @Test
    void findTweetById_shouldReturnTweet() {
        when(tweetRepository.findById(1L)).thenReturn(Optional.of(tweet));

        when(tweet.getContent()).thenReturn("Hello");
        when(tweet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        when(tweet.getStatistics()).thenReturn(statistics);
        when(statistics.getTweet()).thenReturn(tweet);
        TweetResponse result = tweetServiceImpl.findTweetById(1L);
        assertEquals("Hello", result.content());
    }

    @Test
    void testException() {
        when(tweetRepository.findById(99L)).thenReturn(Optional.empty());

        TweetNotFoundException exception = assertThrows(
                TweetNotFoundException.class,
                () -> tweetServiceImpl.findTweetById(99L)
        );
    }

    @Test
    void removeTweetById_shouldRemoveTweet() {
        when(tweetRepository.findById(1L)).thenReturn(Optional.of(tweet));

        when(tweet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);
        tweetServiceImpl.removeTweet(1L, 1L);
        verify(tweetRepository).delete(tweet);
    }

    @Test
    void removeTweetById_shouldThrowWhenUserIsNotOwner() {
        when(tweetRepository.findById(1L)).thenReturn(Optional.of(tweet));

        when(tweet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);

        assertThrows(
                ForbiddenException.class,
                () -> tweetServiceImpl.removeTweet(1L, 2L)
        );
    }

    @Test
    void findAllTweets_shouldReturnPaginatedTweets() {
        Tweet tweet = mock(Tweet.class);
        Page<Tweet> page = new PageImpl<>(List.of(tweet));

        when(tweetRepository.findAll(any(Pageable.class))).thenReturn(page);

        when(tweet.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);
        when(tweet.getStatistics()).thenReturn(statistics);
        when(statistics.getTweet()).thenReturn(tweet);
        Page<TweetResponse> result = tweetServiceImpl.findAllTweets(page.getPageable());
        assertEquals(1, result.getTotalPages());
    }

    @Test
    void update_shouldUpdateTweet() {
        when(tweetRepository.findById(1L)).thenReturn(Optional.of(tweet));
        when(tweet.getUser()).thenReturn(user);
        when(user.getUserName()).thenReturn("1");
        when(tweet.getStatistics()).thenReturn(statistics);
        when(statistics.getTweet()).thenReturn(tweet);
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("1");

        SecurityContextHolder.setContext(securityContext);

        UpdateTweetRequest dto =  mock(UpdateTweetRequest.class);
        when(dto.content()).thenReturn("Updated content");
        tweetServiceImpl.update(1L, dto);
    }

    @Test
    void retweet_shouldRetweetTweet() {
        when(tweetRepository.findById(1L)).thenReturn(Optional.of(tweet));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(tweetRepository.findByParentIdAndUserId(1L, 1L))
                .thenReturn(Optional.empty());

        when(tweet.getStatistics()).thenReturn(statistics);
        when(statistics.getRetweets()).thenReturn(0L);
        when(statistics.getTweet()).thenReturn(tweet);

        tweetServiceImpl.retweet(1L, 1L);

    }
    @Test
    void retweet_shouldUnRetweetTweet() {
        when(tweetRepository.findById(1L)).thenReturn(Optional.of(tweet));
        when(tweetRepository.findByParentIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(tweet));

        when(tweet.getId()).thenReturn(1L);

        when(statisticsRepository.findByTweetId(1L))
                .thenReturn(Optional.of(statistics));
        when(statistics.getRetweets()).thenReturn(1L);

        tweetServiceImpl.unretweet(1L, 1L);
    }

    @Test
    void quoteTweet_shouldQuoteTweet() {
        when(tweetRepository.findById(1L)).thenReturn(Optional.of(tweet));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));


        when(statisticsRepository.findByTweetId(1L))
                .thenReturn(Optional.of(statistics));


        tweetServiceImpl.quoteTweet(1L, 2L,tweet.getContent());
    }

    @Test
    void findAllTweets_shouldfindAllTweetsByUserId() {
        Tweet tweet = mock(Tweet.class);
        Page<Tweet> page = new PageImpl<>(List.of(tweet));

        when(tweetRepository.findByUserId(1L,Pageable.unpaged())).thenReturn(page);

        Page<Tweet> result = tweetServiceImpl.findAllTweetsByUserId(1L,Pageable.unpaged());
        assertEquals(1, result.getTotalPages());
    }

}
