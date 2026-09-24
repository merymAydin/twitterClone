package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Follower;
import com.example.twitter_challenge.Entity.Notification;
import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.FollowerRepository;
import com.example.twitter_challenge.Repository.NotificationRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.FollowerService;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.dto.request.CreateFollowerRequest;
import com.example.twitter_challenge.dto.response.BookmarkResponse;
import com.example.twitter_challenge.dto.response.FollowerResponse;
import com.example.twitter_challenge.dto.response.NotificationResponse;
import com.example.twitter_challenge.exception.*;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FollowerServiceImpl implements FollowerService {
    private final FollowerRepository followerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FollowerServiceImpl(FollowerRepository followerRepository, UserRepository userRepository, NotificationService notificationService) {
        this.followerRepository = followerRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    @Override
    public FollowerResponse createFollower(CreateFollowerRequest request) {
        String curName = SecurityContextHolder.getContext().getAuthentication().getName();

        User followerUser = userRepository.findByUserName(curName)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "User with username " + curName + " not found"
                        )
                );

        if (followerUser.getId().equals(request.followingId())) {
            throw new FollowerSelfFollowException("You can't follow yourself");
        }


        Optional<Follower> existingFollower = followerRepository.findByFollowerIdAndFollowingId(
                followerUser.getId(),
                request.followingId()
        );
        if (existingFollower.isPresent()) {
            throw new FollowerAlreadyExistsException(
                    "User " + followerUser.getId() + " has already followed user " + request.followingId()
            );
        }
        User followingUser = userRepository.findById(request.followingId())
                .orElseThrow(() ->
                        new UserNotFoundException("User with " + request.followingId() + " not found")
                );


        Follower follower = new Follower(followerUser,followingUser);
        Follower savedFollower = followerRepository.save(follower);

         notificationService.create(savedFollower.getFollower().getId(),savedFollower.getFollowing().getId(), NotificationType.FOLLOW, "started following you");


        return new FollowerResponse(savedFollower.getFollower().getId(), savedFollower.getFollowing().getId());
    }


    @Transactional
    @Override
    public void removeFollower(CreateFollowerRequest  request) {

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User followerUser = userRepository.findByUserName(currentUsername)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "User with username " + currentUsername + " not found"
                        )
                );
        Follower follower = followerRepository.findByFollowerIdAndFollowingId(followerUser.getId(),request.followingId()).orElseThrow(
                ()->new FollowerNotFoundException(
                        "Follower relationship between user "
                                + followerUser.getId()
                                + " and user "
                                + request.followingId()
                                + " not found"
                )
        );


        followerRepository.delete(follower);
    }

    @Override
    public List<FollowerResponse> findAllFollowers() {
        return followerRepository.findAll()
                .stream()
                .map(follower -> new FollowerResponse(
                        follower.getFollower().getId(),
                        follower.getFollowing().getId()
                ))
                .toList();
    }



    @Override
    public FollowerResponse findFollowerById(Long id) {
        Follower follower = followerRepository.findById(id).orElseThrow(()->new FollowerNotFoundException("Follower Not Found"));
        return new FollowerResponse(follower.getFollower().getId(), follower.getFollowing().getId());
    }

}
