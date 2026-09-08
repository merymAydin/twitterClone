package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.request.CreateUserRequest;
import com.example.twitter_challenge.dto.request.UpdateUserRequest;
import com.example.twitter_challenge.dto.response.UserResponse;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    @Autowired
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
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
}
