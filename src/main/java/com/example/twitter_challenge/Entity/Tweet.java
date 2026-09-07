package com.example.twitter_challenge.Entity;


import com.example.twitter_challenge.Utils.Commons.Location;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Embedded;
import org.hibernate.annotations.Comments;

import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(schema = "public", name = "tweets")

public class Tweet extends EntityBase{

    private String content;

    @ManyToOne(cascade = {CascadeType.REFRESH,CascadeType.DETACH,CascadeType.MERGE,CascadeType.PERSIST},
            fetch =FetchType.LAZY
    )
    @JoinColumn(name = "user_id")
    private User user;
    @Embedded
    private Location location;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Tweet parent;


    //retweet/quote
    @OneToMany(mappedBy = "tweet")
    private List<Comment> comments;
    //likes
    @OneToMany(mappedBy = "tweet")
    private List<Likes> likes;

}
