package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.response.NotificationResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private NotificationService  notificationService;
    private UserService userService;
    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }
    @GetMapping
    public Page<NotificationResponse> getNotifications(Pageable pageable) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userName = (String) authentication.getPrincipal();
        UserResponse user = userService.findByUserName(userName);
        return notificationService.findByRecipientId(user.userId(), pageable);
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable Long id){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userName = (String) authentication.getPrincipal();
        UserResponse user = userService.findByUserName(userName);
        return notificationService.markAsRead(id, user.userId());
    }
}
