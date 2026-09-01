package com.example.twitter_challenge.Entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "public", name = "likes")
public class Likes extends EntityBase{

    //todo: teknik borc -> Tweet classi yazinca burasi ile oneToMany seklinde bagla
    private Integer tweet_id;


    //todo: teknik borc -> User classi yazinca burasi ile oneToMany seklinde bagla
    private Integer user_id;

    //private User user;
    //private Tweet tweet;
}
