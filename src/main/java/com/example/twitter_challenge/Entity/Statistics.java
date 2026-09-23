package com.example.twitter_challenge.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(schema = "public", name = "statistics")
public class Statistics extends EntityBase{
    @OneToOne
    @JoinColumn(name = "tweet_id", unique = true)
    private Tweet tweet;
    private Long views;
    private Long likes;
    private Long comments;
    private Long bookmarks;
    private Long retweets;
    @Column(name = "quotes", nullable = false)
    private Long quotes = 0L;
}


