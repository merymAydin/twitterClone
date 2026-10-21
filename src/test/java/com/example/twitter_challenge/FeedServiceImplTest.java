package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.Follower;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.FollowerRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Service.FeedServiceImpl;
import com.example.twitter_challenge.Service.TweetResponseMapper;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.example.twitter_challenge.dto.response.TweetResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceImplTest {

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private FollowerRepository followerRepository;

    @Mock
    private SubscriptionService subscriptionService;

    private FeedServiceImpl feedService;

    @BeforeEach
    void setUp() {
        feedService = new FeedServiceImpl(
                tweetRepository,
                followerRepository,
                new TweetResponseMapper(subscriptionService)
        );
    }

    @Test
    void feedIncludesCurrentUserAndOnlyActiveFollowedUsers() {
        User current = user(1L, false);
        User followed = user(2L, false);
        User deleted = user(3L, true);
        Follower activeFollow = new Follower(current, followed);
        Follower deletedFollow = new Follower(current, deleted);

        when(followerRepository.findByFollowerId(1L))
                .thenReturn(List.of(activeFollow, deletedFollow));
        when(tweetRepository.findVisibleByUserIdIn(any(), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of()));

        Page<?> result = feedService.getTweets(1L, Pageable.unpaged());

        assertTrue(result.isEmpty());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(tweetRepository)
                .findVisibleByUserIdIn(captor.capture(), eq(Pageable.unpaged()));
        assertEquals(List.of(1L, 2L), captor.getValue());
    }

    @Test
    void homeFeedNeverUsesTheExploreQuery() {
        when(followerRepository.findByFollowerId(1L)).thenReturn(List.of());
        when(tweetRepository.findVisibleByUserIdIn(any(), any())).thenReturn(new PageImpl<>(List.of()));

        feedService.getTweets(1L, PageRequest.of(0, 10));

        verify(tweetRepository, never()).findExplore(any());
    }

    @Test
    void exploreUsesEveryAuthorAndIgnoresTheFollowGraph() {
        when(tweetRepository.findExplore(any())).thenReturn(new PageImpl<>(List.of()));

        feedService.getExploreTweets(PageRequest.of(0, 10));

        verify(tweetRepository).findExplore(any());
        verify(tweetRepository, never()).findVisibleByUserIdIn(any(), any());
        verify(followerRepository, never()).findByFollowerId(any());
    }

    @Test
    void exploreIgnoresAClientSuppliedSortSoTheOrderStaysNewestFirst() {
        when(tweetRepository.findExplore(any())).thenReturn(new PageImpl<>(List.of()));

        feedService.getExploreTweets(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "content"))
        );

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(tweetRepository).findExplore(captor.capture());

        Pageable used = captor.getValue();
        assertTrue(used.getSort().isUnsorted(), "the explore order is defined by the query itself");
        assertEquals(0, used.getPageNumber());
        assertEquals(10, used.getPageSize());
    }

    @Test
    void exploreReturnsTheTweetsOfEveryAuthorWithTheCorrectPremiumFlag() {
        Tweet premiumTweet = tweet(10L, user(1L, "premium"));
        Tweet regularTweet = tweet(11L, user(2L, "regular"));
        when(tweetRepository.findExplore(any())).thenReturn(
                new PageImpl<>(List.of(premiumTweet, regularTweet), PageRequest.of(0, 10), 2)
        );
        when(subscriptionService.findPremiumUserIds(any())).thenReturn(Set.of(1L));

        Page<TweetResponse> page = feedService.getExploreTweets(PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().get(0).premium());
        assertFalse(page.getContent().get(1).premium());
        assertEquals("premium", page.getContent().get(0).authorUsername());
        assertEquals("regular", page.getContent().get(1).authorUsername());
    }

    @Test
    void exploreResolvesThePremiumFlagsOfTheWholePageWithASingleQuery() {
        when(tweetRepository.findExplore(any())).thenReturn(
                new PageImpl<>(
                        List.of(
                                tweet(10L, user(1L, "a")),
                                tweet(11L, user(2L, "b")),
                                tweet(12L, user(1L, "a"))
                        ),
                        PageRequest.of(0, 10),
                        3
                )
        );
        when(subscriptionService.findPremiumUserIds(any())).thenReturn(Set.of(1L));

        feedService.getExploreTweets(PageRequest.of(0, 10));

        verify(subscriptionService, times(1)).findPremiumUserIds(any());
    }

    @Test
    void exploreKeepsThePageAndSizeOfTheRequest() {
        when(tweetRepository.findExplore(any())).thenReturn(new PageImpl<>(List.of()));

        feedService.getExploreTweets(PageRequest.of(2, 5));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(tweetRepository).findExplore(captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(5, captor.getValue().getPageSize());
    }

    private User user(Long id, boolean deleted) {
        User user = new User();
        user.setId(id);
        user.setIsDeleted(deleted);
        return user;
    }

    private User user(Long id, String username) {
        User user = user(id, false);
        user.setUserName(username);
        return user;
    }

    private Tweet tweet(Long id, User author) {
        Tweet tweet = new Tweet();
        tweet.setId(id);
        tweet.setUser(author);
        tweet.setContent("content " + id);
        tweet.setIsDeleted(false);
        return tweet;
    }
}
