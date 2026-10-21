package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Notification;
import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.NotificationRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.dto.response.NotificationResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import com.example.twitter_challenge.exception.NotificationNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Page<NotificationResponse> findByRecipientId(Long recipientId, Pageable pageable) {
        requireActiveUser(recipientId);
        return notificationRepository.findByRecipientIdAndIsDeletedFalse(recipientId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    @Override
    public NotificationResponse create(
            Long senderId,
            Long recipientId,
            NotificationType type,
            String message
    ) {
        User sender = requireActiveUser(senderId);
        User recipient = requireActiveUser(recipientId);
        if (sender.getId().equals(recipient.getId())) {
            // A user action on their own content must not create a self-notification.
            throw new IllegalArgumentException("Cannot create a notification for the same user");
        }

        Notification notification = new Notification();
        notification.setSender(sender);
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setIsDeleted(false);
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    @Override
    public NotificationResponse markAsRead(Long notificationId, Long recipientId) {
        Notification notification = notificationRepository.findByIdAndIsDeletedFalse(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found"));
        if (!notification.getRecipient().getId().equals(recipientId)) {
            throw new ForbiddenException("You are not allowed to update this notification");
        }
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    private User requireActiveUser(Long userId) {
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new UserNotFoundException("User with " + userId + " not found"));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getSender().getId(),
                notification.getMessage(),
                notification.getType(),
                notification.isRead()
        );
    }
}
