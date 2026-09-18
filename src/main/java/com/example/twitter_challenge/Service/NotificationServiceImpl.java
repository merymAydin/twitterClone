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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {
    private final UserRepository userRepository;
    private NotificationRepository notificationRepository;
    public NotificationServiceImpl(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Page<NotificationResponse> findByRecipientId(Long recipientId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository.findByRecipientId(recipientId, pageable);
        return notifications.map(notification -> new NotificationResponse(
                notification.getSender().getId(),
                notification.getMessage(),
                notification.getType(),
                notification.isRead()
        ));
    }

    @Override
    public NotificationResponse create(Long senderId, Long recipientId, NotificationType type, String message) {
        User sender = userRepository.findById(senderId).orElseThrow(()->new UserNotFoundException("User not found"));
        User recipient = userRepository.findById(recipientId).orElseThrow(()->new UserNotFoundException("User not found"));
        Notification notification = new Notification();
        notification.setSender(sender);
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setMessage(message);
        notification.setRead(false);
        Notification savedNotification = notificationRepository.save(notification);
        return new NotificationResponse(savedNotification.getSender().getId(), savedNotification.getMessage(),
                savedNotification.getType(), savedNotification.isRead());
    }

    @Override
    public NotificationResponse markAsRead(Long notificationId, Long recipientId) {
        Notification notification = notificationRepository.findById(notificationId).orElseThrow(() -> new NotificationNotFoundException("Notification not found"));
        if(!notification.getRecipient().getId().equals(recipientId)){
            throw new ForbiddenException("You are not allowed to update this notification");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        return new NotificationResponse(notification.getSender().getId(), notification.getMessage(), notification.getType(), notification.isRead());
    }
}
