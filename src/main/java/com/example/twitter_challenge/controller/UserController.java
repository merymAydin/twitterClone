package com.example.twitter_challenge.controller;


import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.request.CreateUserRequest;
import com.example.twitter_challenge.dto.request.UpdateUserRequest;
import com.example.twitter_challenge.dto.response.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
    @PostMapping
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest user) {
        return userService.save(user);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.removeUser(id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping
    public List<UserResponse> findAll() {
        return userService.findAll();
    }
    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable Long id,@Valid @RequestBody UpdateUserRequest request){
        return userService.update(id, request);
    }

    @GetMapping("/search")
    public Page<UserResponse> getByUserNameContaining(@RequestParam String keyword, Pageable pageable) {
        return userService.findByUserNameContaining(keyword,pageable);
    }
}
