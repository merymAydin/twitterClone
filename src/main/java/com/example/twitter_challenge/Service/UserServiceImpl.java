package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.request.CreateUserRequest;
import com.example.twitter_challenge.dto.request.UpdateUserRequest;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.dto.response.UserResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Autowired
    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    @Override
    public UserResponse findById(Long id) {
        User user = userRepository.findById(id).orElseThrow(()->new UserNotFoundException("User with" + id +"not found"));
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new UserNotFoundException("User with" + id +"not found");
        }
        return new UserResponse(user.getId(), user.getUserName(),user.getEmail(),user.getBirthday(),user.getLocation(), user.getBio(), user.getPhoto(), user.getBanner());
    }

    @Override
    public UserResponse save(CreateUserRequest request) {
        User user = new User();
        user.setUserName(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setBirthday(request.birthday());
        user.setLocation(request.location());
        user.setBio(request.bio());
        user.setPhoto(request.photo());
        user.setBanner(request.banner());
        user.setIsDeleted(false);
        userRepository.save(user);
        return new UserResponse(user.getId(), user.getUserName(),user.getEmail(),user.getBirthday(),user.getLocation(), user.getBio(), user.getPhoto(), user.getBanner() );
    }


    @Override
    public void removeUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(()->new UserNotFoundException("User with" + id +"not found"));
        String curName = SecurityContextHolder.getContext().getAuthentication().getName();
        if(!curName.equals(user.getUserName())){
            throw new ForbiddenException("You are not allowed to remove this user");
        }
        user.setIsDeleted(true);
        userRepository.save(user);
    }

    @Override
    public List<UserResponse> findAll() {
        return userRepository.findAll()
                .stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getUserName(),
                        user.getEmail(),
                        user.getBirthday(),
                        user.getLocation(),
                        user.getBio(),
                        user.getPhoto(),
                        user.getBanner()
                ))
                .toList();
    }

    @Override
    public UserResponse  update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(()->new UserNotFoundException("User with " + id + " not found"));
        String curName = SecurityContextHolder.getContext().getAuthentication().getName();
        if(!curName.equals(user.getUserName())){
            throw new ForbiddenException("You are not allowed to update this user");
        }
        user.setUserName(request.username());
        user.setEmail(request.email());
        user.setBirthday(request.birthday());
        user.setLocation(request.location());
        user.setBio(request.bio());
        user.setPhoto(request.photo());
        user.setBanner(request.banner());
        userRepository.save(user);
        return new UserResponse (user.getId(), user.getUserName(), user.getEmail(),  user.getBirthday(),user.getLocation(), user.getBio(), user.getPhoto(), user.getBanner());
    }

    @Override
    public UserResponse findByUserName(String username) {
        User user = userRepository.findByUserName(username).orElseThrow(()->new UserNotFoundException("User with " + username + " not found"));
        return new UserResponse (user.getId(), user.getUserName(), user.getEmail(),  user.getBirthday(),user.getLocation(), user.getBio(), user.getPhoto(), user.getBanner());
    }

    @Override
    public Page<UserResponse> findByUserNameContaining(String userName, Pageable pageable) {
        Page<User> users = userRepository.findByUserNameContaining(userName, pageable);
        return users.map(user -> new UserResponse(
                user.getId(),user.getUserName(),user.getEmail(),user.getBirthday(),user.getLocation(),user.getBio(),user.getPhoto(),user.getBanner()
        ));
    }


}
