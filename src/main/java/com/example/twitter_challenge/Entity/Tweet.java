package com.example.twitter_challenge.Entity;


import com.example.twitter_challenge.Utils.Commons.Location;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(schema = "public", name = "tweets")
public class Tweet extends EntityBase{

    private String content;

//    //todo : teknik borc-> user classini bagla
//    private Long user_id;

    @ManyToOne(cascade = {CascadeType.REFRESH,CascadeType.DETACH,CascadeType.MERGE,CascadeType.PERSIST},
            fetch =FetchType.LAZY
    )
    @JoinColumn(name = "user_id")
    private User user;

    private Location location;

    //retweet
    private Long parent_id;


    //comments
    //likes

}
