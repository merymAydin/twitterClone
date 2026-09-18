package com.example.twitter_challenge.dto.response;

import com.example.twitter_challenge.Entity.NotificationType;

public record NotificationResponse(Long senderId, String message, NotificationType type, boolean isRead) {

}
