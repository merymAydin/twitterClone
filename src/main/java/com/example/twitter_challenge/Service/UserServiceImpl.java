package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    @Autowired
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Override
    public User findById(Long id) {
        User user = userRepository.findById(id).orElseThrow(()->new UserNotFoundException("User with" + id +"not found"));
        if(user.getIsDeleted()){
            throw new UserNotFoundException("User with" + id +"not found");
        }
        return user;
    }
    @Override
    public User save(User user) {
        return userRepository.save(user);
    }

    @Override
    public void removeUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(()->new UserNotFoundException("User with" + id +"not found"));
        user.setIsDeleted(true);
        userRepository.save(user);
    }
}
