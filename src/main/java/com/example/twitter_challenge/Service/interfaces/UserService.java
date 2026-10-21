package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.dto.request.CreateUserRequest;
import com.example.twitter_challenge.dto.request.UpdateUserRequest;
import com.example.twitter_challenge.dto.response.PublicUserResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    UserResponse findById(Long id);

    PublicUserResponse findPublicById(Long id);

    UserResponse save(CreateUserRequest request);

    void removeUser(Long id);

    List<UserResponse> findAll();

    List<PublicUserResponse> findAllPublic();

    UserResponse update(Long id, UpdateUserRequest request);

    UserResponse findByUserName(String username);

    UserResponse findByEmail(String email);

    UserResponse findCurrentUser();

    Page<UserResponse> findByUserNameContaining(String userName, Pageable pageable);

    Page<PublicUserResponse> searchPublic(String userName, Pageable pageable);
}
