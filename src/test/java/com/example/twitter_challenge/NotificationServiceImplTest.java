package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.Notification;
import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.NotificationRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.NotificationServiceImpl;
import com.example.twitter_challenge.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void selfNotificationsAreRejected() {
        User user = user(1L);
        when(userRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(user));

        assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.create(1L, 1L, NotificationType.LIKE, "self")
        );
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notificationCannotBeMarkedReadByAnotherUser() {
        User recipient = user(1L);
        User sender = user(2L);
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setSender(sender);
        notification.setRead(false);
        when(notificationRepository.findByIdAndIsDeletedFalse(9L))
                .thenReturn(Optional.of(notification));

        assertThrows(
                ForbiddenException.class,
                () -> notificationService.markAsRead(9L, 2L)
        );
        verify(notificationRepository, never()).save(notification);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setIsDeleted(false);
        return user;
    }
}
