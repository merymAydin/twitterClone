package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

}
