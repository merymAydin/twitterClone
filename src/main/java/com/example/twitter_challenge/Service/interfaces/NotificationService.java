package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    Page<NotificationResponse> findByRecipientId(Long recipientId, Pageable pageable);
    NotificationResponse create(Long senderId,Long recipientId, NotificationType type, String message);
    NotificationResponse markAsRead(Long notificationId, Long recipientId);
}
