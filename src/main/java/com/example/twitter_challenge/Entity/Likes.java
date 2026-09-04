package com.example.twitter_challenge.Entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Data
@AllArgsConstructor
@Table(schema = "public", name = "likes",uniqueConstraints = @UniqueConstraint(columnNames = {"tweet_id", "user_id"}))
public class Likes extends EntityBase{

    @ManyToOne
    @JoinColumn(name = "tweet_id")
    private Tweet tweet;



    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

}
