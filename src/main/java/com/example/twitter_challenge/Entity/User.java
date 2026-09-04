package com.example.twitter_challenge.Entity;


import com.example.twitter_challenge.Utils.Commons.Location;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Embedded;

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
    @Embedded
    private Location location;
    private String bio;
    private String photo;
    private String banner;


    @OneToMany(fetch = FetchType.LAZY, mappedBy = "user")

    private List<Tweet> tweets;

}
