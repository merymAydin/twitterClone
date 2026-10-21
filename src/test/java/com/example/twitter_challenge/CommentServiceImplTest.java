package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.Comment;
import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.CommentRepository;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.CommentServiceImpl;
import com.example.twitter_challenge.Service.MentionNotifier;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.dto.request.CreateCommentRequest;
import com.example.twitter_challenge.dto.request.UpdateCommentRequest;
import com.example.twitter_challenge.exception.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class CommentServiceImplTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private NotificationService notificationService;

    private CommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        // The real mention notifier is used so a mention in a comment is handled exactly like in
        // production. The user repository is a stub without usernames, so no mention resolves.
        commentService = new CommentServiceImpl(
                commentRepository,
                userRepository,
                tweetRepository,
                statisticsRepository,
                notificationService,
                new MentionNotifier(userRepository, notificationService)
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void commentingOnAnotherUsersTweetNotifiesTheAuthor() {
        User current = user(1L, "alice@example.com");
        User author = user(2L, "bob@example.com");
        Tweet tweet = tweet(10L, author);
        Statistics statistics = new Statistics();
        statistics.setComments(2L);

        authenticateAs(current.getEmail());
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(current.getEmail()))
                .thenReturn(Optional.of(current));
        when(tweetRepository.findVisibleById(10L)).thenReturn(Optional.of(tweet));
        when(commentRepository.save(any(Comment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(statisticsRepository.findByTweetIdForUpdate(10L)).thenReturn(Optional.of(statistics));

        commentService.createComment(new CreateCommentRequest(10L, "Nice post"));

        assertEquals(3L, statistics.getComments());
        verify(notificationService).create(1L, 2L, NotificationType.COMMENT, "Commented on your post");
    }

    @Test
    void commentingOnOwnTweetDoesNotNotifyYourself() {
        User current = user(1L, "alice@example.com");
        Tweet tweet = tweet(10L, current);
        Statistics statistics = new Statistics();
        statistics.setComments(0L);

        authenticateAs(current.getEmail());
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(current.getEmail()))
                .thenReturn(Optional.of(current));
        when(tweetRepository.findVisibleById(10L)).thenReturn(Optional.of(tweet));
        when(commentRepository.save(any(Comment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(statisticsRepository.findByTweetIdForUpdate(10L)).thenReturn(Optional.of(statistics));

        commentService.createComment(new CreateCommentRequest(10L, "My own post"));

        verify(notificationService, never()).create(any(), any(), any(), any());
    }

    @Test
    void aMentionInACommentNotifiesTheMentionedUser() {
        User current = user(1L, "alice@example.com");
        User author = user(2L, "bob@example.com");
        User mentioned = user(3L, "carol");
        mentioned.setUserName("carol");
        Tweet tweet = tweet(10L, author);
        Statistics statistics = new Statistics();
        statistics.setComments(0L);

        authenticateAs(current.getEmail());
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(current.getEmail()))
                .thenReturn(Optional.of(current));
        when(tweetRepository.findVisibleById(10L)).thenReturn(Optional.of(tweet));
        when(commentRepository.save(any(Comment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(statisticsRepository.findByTweetIdForUpdate(10L)).thenReturn(Optional.of(statistics));
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(java.util.List.of(mentioned));

        commentService.createComment(new CreateCommentRequest(10L, "hey @carol"));

        verify(notificationService)
                .create(1L, 3L, NotificationType.MENTION, "You were mentioned in a comment");
    }

    @Test
    void theAuthorOfTheCommentedTweetIsNotNotifiedTwice() {
        User current = user(1L, "alice@example.com");
        User author = user(2L, "bob");
        author.setUserName("bob");
        Tweet tweet = tweet(10L, author);
        Statistics statistics = new Statistics();
        statistics.setComments(0L);

        authenticateAs(current.getEmail());
        when(userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(current.getEmail()))
                .thenReturn(Optional.of(current));
        when(tweetRepository.findVisibleById(10L)).thenReturn(Optional.of(tweet));
        when(commentRepository.save(any(Comment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(statisticsRepository.findByTweetIdForUpdate(10L)).thenReturn(Optional.of(statistics));
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(java.util.List.of(author));

        commentService.createComment(new CreateCommentRequest(10L, "hey @bob"));

        // The comment notification is sent, the mention notification is not: the author of the
        // commented tweet already knows about this action.
        verify(notificationService)
                .create(1L, 2L, NotificationType.COMMENT, "Commented on your post");
        verify(notificationService, never())
                .create(any(), any(), org.mockito.ArgumentMatchers.eq(NotificationType.MENTION), any());
    }

    @Test
    void userCannotUpdateAnotherUsersComment() {
        Comment comment = new Comment(
                tweet(10L, user(3L, "carol@example.com")),
                user(2L, "bob@example.com"),
                "original"
        );
        when(commentRepository.findByIdAndIsDeletedFalse(5L)).thenReturn(Optional.of(comment));

        assertThrows(
                ForbiddenException.class,
                () -> commentService.update(5L, new UpdateCommentRequest("hijacked"), 1L)
        );
        verify(commentRepository, never()).save(comment);
    }

    @Test
    void userCannotDeleteAnotherUsersComment() {
        Comment comment = new Comment(
                tweet(10L, user(3L, "carol@example.com")),
                user(2L, "bob@example.com"),
                "original"
        );
        when(commentRepository.findByIdAndIsDeletedFalse(5L)).thenReturn(Optional.of(comment));

        assertThrows(ForbiddenException.class, () -> commentService.removeComment(5L, 1L));
        verify(commentRepository, never()).save(comment);
    }

    @Test
    void deletingCommentSoftDeletesAndDecrementsCounter() {
        Comment comment = new Comment(
                tweet(10L, user(3L, "carol@example.com")),
                user(2L, "bob@example.com"),
                "original"
        );
        Statistics statistics = new Statistics();
        statistics.setComments(1L);
        when(commentRepository.findByIdAndIsDeletedFalse(5L)).thenReturn(Optional.of(comment));
        when(statisticsRepository.findByTweetIdForUpdate(10L)).thenReturn(Optional.of(statistics));

        commentService.removeComment(5L, 2L);

        assertEquals(0L, statistics.getComments());
        org.junit.jupiter.api.Assertions.assertTrue(comment.getIsDeleted());
        verify(commentRepository).save(comment);
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
