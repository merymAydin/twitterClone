package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.Bookmark;
import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.BookmarkRepository;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.BookmarkServiceImpl;
import com.example.twitter_challenge.dto.request.CreateBookmarkRequest;
import com.example.twitter_challenge.dto.response.BookmarkResponse;
import com.example.twitter_challenge.exception.BookmarkAlreadyExistsException;
import com.example.twitter_challenge.exception.BookmarkNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceImplTest {

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @InjectMocks
    private BookmarkServiceImpl bookmarkService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rebookmarkingSoftDeletedRowReactivatesInsteadOfInserting() {
        User current = user(1L, "alice@example.com");
        User author = user(2L, "bob@example.com");
        Tweet tweet = tweet(10L, author);
        Bookmark existing = new Bookmark(current, tweet);
        existing.setId(5L);
        existing.setIsDeleted(true);
        Statistics statistics = new Statistics();
        statistics.setLikes(0L);
        statistics.setBookmarks(4L);

        authenticateAs(current.getEmail());
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(current.getEmail()))
                .thenReturn(Optional.of(current));
        when(tweetRepository.findVisibleById(10L)).thenReturn(Optional.of(tweet));
        when(bookmarkRepository.findByTweetIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(existing));
        when(statisticsRepository.findByTweetIdForUpdate(10L))
                .thenReturn(Optional.of(statistics));

        BookmarkResponse response = bookmarkService.createBookmark(new CreateBookmarkRequest(10L));

        assertEquals(1L, response.userId());
        assertEquals(10L, response.tweetId());
        verify(bookmarkRepository).saveAndFlush(existing);
        assertEquals(5L, statistics.getBookmarks());
        verify(bookmarkRepository, never()).save(any(Bookmark.class));
    }

    @Test
    void activeDuplicateBookmarkIsRejected() {
        User current = user(1L, "alice@example.com");
        Tweet tweet = tweet(10L, user(2L, "bob@example.com"));
        Bookmark existing = new Bookmark(current, tweet);
        existing.setIsDeleted(false);

        authenticateAs(current.getEmail());
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(current.getEmail()))
                .thenReturn(Optional.of(current));
        when(tweetRepository.findVisibleById(10L)).thenReturn(Optional.of(tweet));
        when(bookmarkRepository.findByTweetIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(existing));

        assertThrows(
                BookmarkAlreadyExistsException.class,
                () -> bookmarkService.createBookmark(new CreateBookmarkRequest(10L))
        );
    }

    @Test
    void bookmarkLookupIsScopedToTheCurrentUser() {
        when(bookmarkRepository.findByIdAndUserIdAndIsDeletedFalse(99L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                BookmarkNotFoundException.class,
                () -> bookmarkService.findBookmarkById(99L, 1L)
        );
    }

    @Test
    void listingOnlyReturnsCurrentUsersVisibleBookmarks() {
        User current = user(1L, "alice@example.com");
        Tweet visibleTweet = tweet(10L, user(2L, "bob@example.com"));
        Tweet deletedTweet = tweet(11L, user(2L, "bob@example.com"));
        deletedTweet.setIsDeleted(true);
        Bookmark visible = new Bookmark(current, visibleTweet);
        Bookmark hidden = new Bookmark(current, deletedTweet);

        when(bookmarkRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(visible, hidden));

        List<BookmarkResponse> result = bookmarkService.findAllBookmarks(1L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).tweetId());
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

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null)
        );
    }
}
