package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.MentionNotifier;
import com.example.twitter_challenge.Service.TweetResponseMapper;
import com.example.twitter_challenge.Service.TweetServiceImpl;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.request.UpdateTweetRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Retweet, quote and mention behaviour of the tweet service.
 */
@ExtendWith(MockitoExtension.class)
class TweetActionsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private NotificationService notificationService;

    private TweetServiceImpl tweetService;

    private User author;
    private Tweet original;

    @BeforeEach
    void setUp() {
        tweetService = new TweetServiceImpl(
                userRepository,
                tweetRepository,
                statisticsRepository,
                notificationService,
                new TweetResponseMapper(mock(SubscriptionService.class)),
                new MentionNotifier(userRepository, notificationService)
        );

        author = new User();
        author.setId(1L);
        author.setUserName("author");
        author.setIsDeleted(false);

        original = new Tweet();
        original.setId(100L);
        original.setUser(author);
        original.setContent("original content");
        original.setIsDeleted(false);
        original.setStatistics(statisticsOf(100L));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("author@example.com", null, java.util.List.of())
        );
    }

    private void useOriginalTweet() {
        org.mockito.Mockito.lenient()
                .when(tweetRepository.findVisibleById(100L))
                .thenReturn(Optional.of(original));
    }

    @Test
    void retweetStoresTheRetweetAndCountsItOnTheOriginal() {
        User retweeter = user(2L, "retweeter");
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(retweeter));
        useOriginalTweet();
        when(tweetRepository.findByParentIdAndUserIdAndContentIsNull(100L, 2L))
                .thenReturn(Optional.empty());
        when(tweetRepository.save(any(Tweet.class))).thenAnswer(call -> {
            Tweet saved = call.getArgument(0);
            saved.setId(500L);
            return saved;
        });
        when(statisticsRepository.save(any(Statistics.class))).thenAnswer(call -> call.getArgument(0));
        when(statisticsRepository.findByTweetIdForUpdate(100L)).thenReturn(Optional.of(statisticsOf(100L)));

        TweetResponse response = tweetService.retweet(100L, 2L);

        ArgumentCaptor<Tweet> captor = ArgumentCaptor.forClass(Tweet.class);
        verify(tweetRepository).save(captor.capture());
        Tweet saved = captor.getValue();
        assertEquals(100L, saved.getParent().getId(), "the retweet keeps the original tweet");
        assertEquals(2L, saved.getUser().getId(), "the retweet belongs to the retweeter");
        assertNull(saved.getContent(), "a retweet has no content of its own");

        Statistics updated = statisticsRepository.findByTweetIdForUpdate(100L).orElseThrow();
        assertEquals(1L, updated.getRetweets());
        assertEquals(500L, response.id());
    }

    @Test
    void aRetweetGetsItsOwnStatisticsRowSoItCanBeLiked() {
        User retweeter = user(2L, "retweeter");
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(retweeter));
        useOriginalTweet();
        when(tweetRepository.findByParentIdAndUserIdAndContentIsNull(100L, 2L))
                .thenReturn(Optional.empty());
        when(tweetRepository.save(any(Tweet.class))).thenAnswer(call -> {
            Tweet saved = call.getArgument(0);
            saved.setId(500L);
            return saved;
        });
        when(statisticsRepository.save(any(Statistics.class))).thenAnswer(call -> call.getArgument(0));
        when(statisticsRepository.findByTweetIdForUpdate(100L)).thenReturn(Optional.of(statisticsOf(100L)));

        tweetService.retweet(100L, 2L);

        ArgumentCaptor<Statistics> captor = ArgumentCaptor.forClass(Statistics.class);
        verify(statisticsRepository, times(2)).save(captor.capture());
        boolean hasCounterRowForTheRetweet = captor.getAllValues().stream()
                .anyMatch(statistics -> statistics.getTweet() != null
                        && statistics.getTweet().getId() != null
                        && statistics.getTweet().getId() == 500L);
        assertTrue(hasCounterRowForTheRetweet, "the retweet needs its own counter row");
    }

    @Test
    void theSameUserCannotRetweetTheSameTweetTwice() {
        User retweeter = user(2L, "retweeter");
        Tweet existing = new Tweet();
        existing.setId(500L);
        existing.setUser(retweeter);
        existing.setParent(original);
        existing.setIsDeleted(false);

        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(retweeter));
        useOriginalTweet();
        when(tweetRepository.findByParentIdAndUserIdAndContentIsNull(100L, 2L))
                .thenReturn(Optional.of(existing));

        assertThrows(ForbiddenException.class, () -> tweetService.retweet(100L, 2L));
        verify(tweetRepository, never()).save(any(Tweet.class));
    }

    @Test
    void unretweetSoftDeletesTheRetweetAndDecreasesTheCounter() {
        User retweeter = user(2L, "retweeter");
        Tweet existing = new Tweet();
        existing.setId(500L);
        existing.setUser(retweeter);
        existing.setParent(original);
        existing.setIsDeleted(false);

        when(tweetRepository.findByParentIdAndUserIdAndContentIsNullAndIsDeletedFalse(100L, 2L))
                .thenReturn(Optional.of(existing));
        when(statisticsRepository.findByTweetIdForUpdate(100L)).thenReturn(Optional.of(statisticsOf(100L)));

        tweetService.unretweet(100L, 2L);

        assertTrue(existing.getIsDeleted());
        Statistics updated = statisticsRepository.findByTweetIdForUpdate(100L).orElseThrow();
        assertEquals(0L, updated.getRetweets());
    }

    @Test
    void quoteStoresTheQuoteWithTheOriginalAndCountsIt() {
        User quoter = user(2L, "quoter");
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(quoter));
        useOriginalTweet();
        when(tweetRepository.save(any(Tweet.class))).thenAnswer(call -> {
            Tweet saved = call.getArgument(0);
            saved.setId(600L);
            return saved;
        });
        when(statisticsRepository.save(any(Statistics.class))).thenAnswer(call -> call.getArgument(0));
        when(statisticsRepository.findByTweetIdForUpdate(100L)).thenReturn(Optional.of(statisticsOf(100L)));

        TweetResponse response = tweetService.quoteTweet(100L, 2L, "my thoughts");

        ArgumentCaptor<Tweet> captor = ArgumentCaptor.forClass(Tweet.class);
        verify(tweetRepository).save(captor.capture());
        assertEquals(100L, captor.getValue().getParent().getId());
        assertEquals("my thoughts", captor.getValue().getContent());
        assertEquals(600L, response.id());

        Statistics updated = statisticsRepository.findByTweetIdForUpdate(100L).orElseThrow();
        assertEquals(1L, updated.getQuotes());
    }

    @Test
    void theQuoteResponseCarriesTheOriginalTweetWithItsAuthor() {
        User quoter = user(2L, "quoter");
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(quoter));
        useOriginalTweet();
        when(tweetRepository.save(any(Tweet.class))).thenAnswer(call -> {
            Tweet saved = call.getArgument(0);
            saved.setId(600L);
            saved.setParent(original);
            return saved;
        });
        when(statisticsRepository.save(any(Statistics.class))).thenAnswer(call -> call.getArgument(0));
        when(statisticsRepository.findByTweetIdForUpdate(100L)).thenReturn(Optional.of(statisticsOf(100L)));

        TweetResponse response = tweetService.quoteTweet(100L, 2L, "my thoughts");

        assertNotNull(response.originalTweet());
        assertEquals(100L, response.originalTweet().id());
        assertEquals("original content", response.originalTweet().content());
        assertEquals(1L, response.originalTweet().userId());
        assertEquals("author", response.originalTweet().authorUsername());
        assertEquals(100L, response.parentId());
    }

    @Test
    void anOrdinaryTweetHasNoOriginalTweet() {
        Tweet plain = new Tweet();
        plain.setId(700L);
        plain.setUser(author);
        plain.setContent("plain");
        plain.setIsDeleted(false);
        plain.setStatistics(statisticsOf(700L));
        when(tweetRepository.findVisibleById(700L)).thenReturn(Optional.of(plain));

        TweetResponse response = tweetService.findTweetById(700L);

        assertNull(response.originalTweet());
    }

    @Test
    void theGenericCreateEndpointRejectsAParentIdSoQuotesStaySeparate() {
        assertThrows(IllegalArgumentException.class, () -> tweetService.createTweet(
                new CreateTweetRequest("reply", null, 100L)
        ));
        verify(tweetRepository, never()).save(any(Tweet.class));
    }

    @Test
    void creatingATweetNotifiesTheMentionedUsers() {
        User bob = user(2L, "bob");
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(any()))
                .thenReturn(Optional.of(author));
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(java.util.List.of(bob));
        when(tweetRepository.save(any(Tweet.class))).thenAnswer(call -> {
            Tweet saved = call.getArgument(0);
            saved.setId(800L);
            return saved;
        });
        when(statisticsRepository.save(any(Statistics.class))).thenAnswer(call -> call.getArgument(0));

        tweetService.createTweet(new CreateTweetRequest("hello @bob", null, null));

        verify(notificationService).create(1L, 2L, NotificationType.MENTION, "You were mentioned in a tweet");
    }

    @Test
    void quotingATweetNotifiesTheMentionedUsers() {
        User quoter = user(2L, "quoter");
        User bob = user(3L, "bob");
        when(userRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(quoter));
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(java.util.List.of(bob));
        useOriginalTweet();
        when(tweetRepository.save(any(Tweet.class))).thenAnswer(call -> {
            Tweet saved = call.getArgument(0);
            saved.setId(600L);
            return saved;
        });
        when(statisticsRepository.save(any(Statistics.class))).thenAnswer(call -> call.getArgument(0));
        when(statisticsRepository.findByTweetIdForUpdate(100L)).thenReturn(Optional.of(statisticsOf(100L)));

        tweetService.quoteTweet(100L, 2L, "hello @bob");

        verify(notificationService).create(2L, 3L, NotificationType.MENTION, "You were mentioned in a quote");
    }

    @Test
    void editingATweetNotifiesTheNewlyMentionedUsers() {
        User bob = user(2L, "bob");
        Tweet own = new Tweet();
        own.setId(100L);
        own.setUser(author);
        own.setContent("old content");
        own.setIsDeleted(false);
        own.setStatistics(statisticsOf(100L));

        when(tweetRepository.findVisibleById(100L)).thenReturn(Optional.of(own));
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(java.util.List.of(bob));

        tweetService.update(100L, new UpdateTweetRequest("now with @bob"), 1L);

        verify(notificationService).create(1L, 2L, NotificationType.MENTION, "You were mentioned in a tweet");
    }

    @Test
    void aRetweetCannotBeEdited() {
        Tweet retweet = new Tweet();
        retweet.setId(500L);
        retweet.setUser(author);
        retweet.setParent(original);
        retweet.setIsDeleted(false);
        when(tweetRepository.findVisibleById(500L)).thenReturn(Optional.of(retweet));

        assertThrows(
                ForbiddenException.class,
                () -> tweetService.update(500L, new UpdateTweetRequest("x"), 1L)
        );
    }

    @Test
    void theRetweetedOriginalIdsOfTheUserAreReturned() {
        when(tweetRepository.findRetweetedOriginalTweetIds(1L)).thenReturn(java.util.List.of(100L, 101L));

        assertEquals(java.util.List.of(100L, 101L), tweetService.findRetweetedTweetIds(1L));
        verify(tweetRepository, times(1)).findRetweetedOriginalTweetIds(1L);
    }

    private Statistics statisticsOf(Long tweetId) {
        Statistics statistics = new Statistics();
        statistics.setTweet(new Tweet());
        statistics.getTweet().setId(tweetId);
        statistics.setViews(0L);
        statistics.setLikes(0L);
        statistics.setComments(0L);
        statistics.setBookmarks(0L);
        statistics.setRetweets(0L);
        statistics.setQuotes(0L);
        statistics.setIsDeleted(false);
        return statistics;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUserName(username);
        user.setIsDeleted(false);
        return user;
    }
}
