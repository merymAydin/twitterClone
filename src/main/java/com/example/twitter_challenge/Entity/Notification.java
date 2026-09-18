package com.example.twitter_challenge.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(schema = "public",name="notifications")
public class Notification extends EntityBase {

    @ManyToOne
    @JoinColumn(name = "recipient_id")
    private User recipient;

    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender;

    private boolean isRead;

    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type;


}
