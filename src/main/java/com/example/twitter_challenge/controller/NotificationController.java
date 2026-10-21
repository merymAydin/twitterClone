package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.response.NotificationResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping
    public Page<NotificationResponse> getNotifications(Pageable pageable) {
        return notificationService.findByRecipientId(currentUserId(), pageable);
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable Long id) {
        return notificationService.markAsRead(id, currentUserId());
    }

    /**
     * The verified JWT principal is the account email, not the username. Resolving it as an email
     * avoids the previous bug where every notification request failed for normal users.
     */
    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserResponse user = userService.findByEmail(authentication.getName());
        return user.userId();
    }
}
