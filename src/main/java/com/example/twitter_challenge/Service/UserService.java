package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.User;

import java.util.Optional;

public interface UserService {
    User findById(Long id);
    User save(User user);
}
