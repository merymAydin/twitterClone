package com.example.twitter_challenge.dto.response;

import com.example.twitter_challenge.Entity.NotificationType;

/**
 * A notification for the signed-in user.
 *
 * <p>{@code id} is the notification id. A client needs it to mark the notification as read
 * ({@code PUT /notifications/{id}/read}), so it is part of the response.</p>
 */
public record NotificationResponse(
        Long id,
        Long senderId,
        String message,
        NotificationType type,
        boolean isRead
) {

}
