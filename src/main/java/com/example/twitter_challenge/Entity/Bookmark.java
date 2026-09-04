package com.example.twitter_challenge.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@Data
@AllArgsConstructor
@Entity
@Table(schema = "public", name = "bookmarks",uniqueConstraints = @UniqueConstraint(columnNames = {"tweet_id", "user_id"}))
public class Bookmark extends EntityBase {
    @ManyToOne
    @JoinColumn(name="user_id")
    private User user;
    @ManyToOne
    @JoinColumn(name="tweet_id")
    private Tweet tweet;
}
