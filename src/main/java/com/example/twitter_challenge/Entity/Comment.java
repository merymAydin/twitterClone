package com.example.twitter_challenge.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(schema = "public", name = "comments")
public class Comment extends EntityBase {
    @ManyToOne
    @JoinColumn(name = "tweet_id")
    private Tweet tweet;



    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String content;


}


