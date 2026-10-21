package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Service.TweetResponseMapper;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.example.twitter_challenge.dto.response.TweetResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TweetResponseMapperTest {

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private TweetResponseMapper mapper;

    @Test
    void mapsTheAuthorInformationAndTheInteractionCounts() {
        User author = user(1L, "alice", "alice.png");
        Tweet tweet = tweet(10L, author, "hello world");

        TweetResponse response = mapper.toResponse(tweet);

        assertEquals(10L, response.id());
        assertEquals("hello world", response.content());
        assertEquals(1L, response.userId());
        assertEquals("alice", response.authorUsername());
        assertEquals("alice.png", response.authorPhoto());
        assertEquals(1L, response.statistics().likes());
        assertEquals(2L, response.statistics().comments());
        assertEquals(3L, response.statistics().bookmarks());
        assertEquals(4L, response.statistics().retweets());
        assertEquals(5L, response.statistics().quotes());
        assertEquals(0L, response.statistics().views());
    }

    @Test
    void premiumFlagIsFalseForAnAuthorWithoutAnEntitledSubscription() {
        when(subscriptionService.findPremiumUserIds(any())).thenReturn(Set.of());

        TweetResponse response = mapper.toResponse(tweet(10L, user(1L, "alice", null), "hi"));

        assertFalse(response.premium());
    }

    @Test
    void premiumFlagIsTrueForAnAuthorWithAnEntitledSubscription() {
        when(subscriptionService.findPremiumUserIds(any())).thenReturn(Set.of(1L));

        TweetResponse response = mapper.toResponse(tweet(10L, user(1L, "alice", null), "hi"));

        assertTrue(response.premium());
    }

    @Test
    void thePremiumFlagOfEveryAuthorOfAPageIsCorrect() {
        User premium = user(1L, "premium", null);
        User regular = user(2L, "regular", null);
        when(subscriptionService.findPremiumUserIds(any())).thenReturn(Set.of(1L));

        Page<TweetResponse> page = mapper.toResponsePage(new PageImpl<>(
                List.of(tweet(10L, premium, "a"), tweet(11L, regular, "b")),
                PageRequest.of(0, 10),
                2
        ));

        assertTrue(page.getContent().get(0).premium());
        assertFalse(page.getContent().get(1).premium());
    }

    @Test
    void thePremiumFlagsOfAWholePageAreResolvedWithASingleQuery() {
        when(subscriptionService.findPremiumUserIds(any())).thenReturn(Set.of(1L));

        mapper.toResponsePage(new PageImpl<>(
                List.of(
                        tweet(10L, user(1L, "a", null), "a"),
                        tweet(11L, user(2L, "b", null), "b"),
                        tweet(12L, user(1L, "a", null), "c")
                ),
                PageRequest.of(0, 10),
                3
        ));

        verify(subscriptionService, times(1)).findPremiumUserIds(any());
    }

    @Test
    void aTweetWithoutStatisticsStillCarriesZeroCounters() {
        Tweet tweet = tweet(10L, user(1L, "alice", null), "no stats");
        tweet.setStatistics(null);

        TweetResponse response = mapper.toResponse(tweet);

        assertEquals(0L, response.statistics().likes());
        assertEquals(0L, response.statistics().views());
        assertEquals(10L, response.statistics().tweetId());
    }

    private User user(Long id, String username, String photo) {
        User user = new User();
        user.setId(id);
        user.setUserName(username);
        user.setPhoto(photo);
        user.setIsDeleted(false);
        return user;
    }

    private Tweet tweet(Long id, User author, String content) {
        Tweet tweet = new Tweet();
        tweet.setId(id);
        tweet.setUser(author);
        tweet.setContent(content);
        tweet.setIsDeleted(false);

        Statistics statistics = new Statistics();
        statistics.setTweet(tweet);
        statistics.setViews(0L);
        statistics.setLikes(1L);
        statistics.setComments(2L);
        statistics.setBookmarks(3L);
        statistics.setRetweets(4L);
        statistics.setQuotes(5L);
        statistics.setIsDeleted(false);
        tweet.setStatistics(statistics);
        return tweet;
    }
}
