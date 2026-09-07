package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.Entity.User;

public interface UserService {
    User findById(Long id);
    User save(User user);
    void removeUser(Long id);
}
