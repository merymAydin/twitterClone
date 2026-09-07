package com.example.twitter_challenge.controller;


import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Service.interfaces.UserService;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.save(user);
    }
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userService.removeUser(id);
    }
}
