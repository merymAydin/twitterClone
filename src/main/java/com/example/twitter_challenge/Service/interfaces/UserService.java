package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.dto.request.CreateUserRequest;
import com.example.twitter_challenge.dto.request.UpdateUserRequest;
import com.example.twitter_challenge.dto.response.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse findById(Long id);
    UserResponse save(CreateUserRequest request);
    void removeUser(Long id);
    List<UserResponse> findAll();
    UserResponse update(Long id, UpdateUserRequest request);

}
