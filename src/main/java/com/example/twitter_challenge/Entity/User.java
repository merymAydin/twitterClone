package com.example.twitter_challenge.Entity;


import com.example.twitter_challenge.Utils.Commons.Location;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(schema = "public", name = "users")
public class User extends EntityBase{

    private String userName;
    private String email;
    private String password;
    private Date birthday;
    private Location location;
    private String bio;
    private String photo;
    private String banner;

    //tweets
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "user")
    private List<Tweet> tweets;

}
