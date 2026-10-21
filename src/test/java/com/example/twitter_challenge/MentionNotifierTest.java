package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.MentionNotifier;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentionNotifierTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private MentionNotifier mentionNotifier;

    @BeforeEach
    void setUp() {
        mentionNotifier = new MentionNotifier(userRepository, notificationService);
    }

    @Test
    void notifiesEveryMentionedUser() {
        User author = user(1L, "author");
        User bob = user(2L, "bob");
        User carol = user(3L, "carol");
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(List.of(bob, carol));

        mentionNotifier.notifyMentionedUsers("hello @bob and @carol", author, "mentioned", Set.of());

        verify(notificationService).create(1L, 2L, NotificationType.MENTION, "mentioned");
        verify(notificationService).create(1L, 3L, NotificationType.MENTION, "mentioned");
    }

    @Test
    void doesNotNotifyTheAuthorAboutThemselves() {
        User author = user(1L, "author");
        User other = user(2L, "other");
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(List.of(author, other));

        mentionNotifier.notifyMentionedUsers("hi @author and @other", author, "mentioned", Set.of());

        verify(notificationService, never()).create(eq(1L), eq(1L), any(), any());
        verify(notificationService, times(1)).create(eq(1L), eq(2L), any(), any());
    }

    @Test
    void createsOnlyOneNotificationForTheSameUserMentionedTwice() {
        User author = user(1L, "author");
        User bob = user(2L, "bob");
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(List.of(bob));

        mentionNotifier.notifyMentionedUsers("@bob @bob @bob", author, "mentioned", Set.of());

        verify(notificationService, times(1)).create(eq(1L), eq(2L), any(), any());
    }

    @Test
    void treatsTheSameUsernameInDifferentCasesAsOneMention() {
        User author = user(1L, "author");
        User bob = user(2L, "bob");
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(List.of(bob));

        mentionNotifier.notifyMentionedUsers("@bob @BOB @Bob", author, "mentioned", Set.of());

        verify(notificationService, times(1)).create(eq(1L), eq(2L), any(), any());
    }

    @Test
    void ignoresUsernamesThatDoNotExist() {
        User author = user(1L, "author");
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(List.of());

        mentionNotifier.notifyMentionedUsers("@nobody @ghost", author, "mentioned", Set.of());

        verify(notificationService, never()).create(anyLong(), anyLong(), any(), any());
    }

    @Test
    void skipsRecipientsThatAlreadyGotANotificationForTheSameAction() {
        User author = user(1L, "author");
        User bob = user(2L, "bob");
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(List.of(bob));

        mentionNotifier.notifyMentionedUsers("@bob", author, "mentioned", Set.of(2L));

        verify(notificationService, never()).create(anyLong(), anyLong(), any(), any());
    }

    @Test
    void resolvesTheMentionedUsersWithASingleQuery() {
        User author = user(1L, "author");
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(List.of());

        mentionNotifier.notifyMentionedUsers("@a @b @c @d", author, "mentioned", Set.of());

        verify(userRepository, times(1))
                .findAllByUserNameIgnoreCaseIn(any());
    }

    @Test
    void passesTheDistinctUsernamesToTheRepository() {
        User author = user(1L, "author");
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(List.of());

        mentionNotifier.notifyMentionedUsers("@bob @alice @bob", author, "mentioned", Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(userRepository)
                .findAllByUserNameIgnoreCaseIn(captor.capture());
        assertTrue(captor.getValue().containsAll(Set.of("alice", "bob")));
        assertEquals(2, captor.getValue().size(), "the same username is only resolved once");
    }

    @Test
    void theUsernameIsLowercasedWithALocaleIndependentMapping() {
        Locale defaultLocale = Locale.getDefault();
        try {
            // The Turkish locale is the default of the IDE run configuration. A plain
            // toLowerCase() would turn "I" into "ı" there and the lookup would never match.
            Locale.setDefault(new Locale("tr", "TR"));
            User author = user(1L, "author");
            when(userRepository.findAllByUserNameIgnoreCaseIn(any())).thenReturn(List.of());

            mentionNotifier.notifyMentionedUsers("@Ilker", author, "mentioned", Set.of());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Collection<String>> captor =
                    ArgumentCaptor.forClass(java.util.Collection.class);
            verify(userRepository).findAllByUserNameIgnoreCaseIn(captor.capture());
            assertTrue(captor.getValue().contains("ilker"),
                    "the username must be lowercased with Locale.ROOT");
            assertTrue(captor.getValue().contains("Ilker"),
                    "the original spelling is kept as well");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void doesNothingForContentWithoutAMention() {
        User author = user(1L, "author");

        mentionNotifier.notifyMentionedUsers("plain text", author, "mentioned", Set.of());

        verify(userRepository, never())
                .findAllByUserNameIgnoreCaseIn(any());
        verifyNoNotificationCreated();
    }

    @Test
    void doesNothingForNullOrBlankContent() {
        User author = user(1L, "author");

        mentionNotifier.notifyMentionedUsers(null, author, "mentioned", Set.of());
        mentionNotifier.notifyMentionedUsers("   ", author, "mentioned", Set.of());

        verifyNoNotificationCreated();
    }

    @Test
    void matchesTheUsernameCharsetOfTheRegistrationValidation() {
        User author = user(1L, "author");
        User bob = user(2L, "bob");
        when(userRepository.findAllByUserNameIgnoreCaseIn(any()))
                .thenReturn(List.of(bob));

        mentionNotifier.notifyMentionedUsers("hi @bob_2 and @b-o-b", author, "mentioned", Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(userRepository)
                .findAllByUserNameIgnoreCaseIn(captor.capture());
        // A hyphen is not part of a username, so "@b-o-b" can only ever match "b".
        assertEquals(Set.of("bob_2", "b"), Set.copyOf(captor.getValue()));
        assertTrue(captor.getValue().stream().noneMatch(name -> name.contains("-")));
    }

    private void verifyNoNotificationCreated() {
        verify(notificationService, never()).create(anyLong(), anyLong(), any(), any());
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUserName(username);
        user.setIsDeleted(false);
        return user;
    }
}
