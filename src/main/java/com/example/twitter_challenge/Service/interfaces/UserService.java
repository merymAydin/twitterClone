package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.dto.request.CreateUserRequest;
import com.example.twitter_challenge.dto.request.UpdateUserRequest;
import com.example.twitter_challenge.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserService {
    UserResponse findById(Long id);
    UserResponse save(CreateUserRequest request);
    void removeUser(Long id);
    List<UserResponse> findAll();
    UserResponse update(Long id, UpdateUserRequest request);
    UserResponse findByUserName(String username);
    Page<UserResponse> findByUserNameContaining(String userName, Pageable pageable);

}
