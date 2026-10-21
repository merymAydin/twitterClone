package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Follower;
import com.example.twitter_challenge.Entity.NotificationType;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.FollowerRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.FollowerService;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.dto.request.CreateFollowerRequest;
import com.example.twitter_challenge.dto.response.FollowerResponse;
import com.example.twitter_challenge.exception.FollowerAlreadyExistsException;
import com.example.twitter_challenge.exception.FollowerNotFoundException;
import com.example.twitter_challenge.exception.FollowerSelfFollowException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FollowerServiceImpl implements FollowerService {
    private final FollowerRepository followerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FollowerServiceImpl(
            FollowerRepository followerRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.followerRepository = followerRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private FollowerResponse toResponse(Follower follower) {
        return new FollowerResponse(
                follower.getFollower().getId(),
                follower.getFollowing().getId()
        );
    }

    @Transactional
    @Override
    public FollowerResponse createFollower(CreateFollowerRequest request) {
        User followerUser = currentUser();
        if (followerUser.getId().equals(request.followingId())) {
            throw new FollowerSelfFollowException("You can't follow yourself");
        }

        User followingUser = userRepository.findByIdAndIsDeletedFalse(request.followingId())
                .orElseThrow(() -> new UserNotFoundException(
                        "User with " + request.followingId() + " not found"
                ));

        if (followerRepository.existsByFollowerIdAndFollowingId(
                followerUser.getId(), followingUser.getId())) {
            throw new FollowerAlreadyExistsException(
                    "User " + followerUser.getId() + " has already followed user " + followingUser.getId()
            );
        }

        Follower follower = new Follower(followerUser, followingUser);
        try {
            followerRepository.saveAndFlush(follower);
        } catch (DataIntegrityViolationException exception) {
            throw new FollowerAlreadyExistsException(
                    "User " + followerUser.getId() + " has already followed user " + followingUser.getId()
            );
        }

        notificationService.create(
                followerUser.getId(),
                followingUser.getId(),
                NotificationType.FOLLOW,
                "started following you"
        );
        return toResponse(follower);
    }

    @Transactional
    @Override
    public void removeFollower(CreateFollowerRequest request) {
        User followerUser = currentUser();
        Follower follower = followerRepository
                .findByFollowerIdAndFollowingId(followerUser.getId(), request.followingId())
                .orElseThrow(() -> new FollowerNotFoundException(
                        "Follower relationship not found"
                ));
        followerRepository.delete(follower);
    }

    @Transactional(readOnly = true)
    @Override
    public List<FollowerResponse> findAllFollowers() {
        return followerRepository.findAll().stream()
                .filter(follower -> !Boolean.TRUE.equals(follower.getFollower().getIsDeleted()))
                .filter(follower -> !Boolean.TRUE.equals(follower.getFollowing().getIsDeleted()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public FollowerResponse findFollowerById(Long id) {
        Follower follower = followerRepository.findById(id)
                .orElseThrow(() -> new FollowerNotFoundException("Follower not found"));
        return toResponse(follower);
    }

    @Transactional(readOnly = true)
    @Override
    public List<FollowerResponse> findFollowersOf(Long userId) {
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new UserNotFoundException("User with " + userId + " not found"));
        return followerRepository.findByFollowingId(userId).stream()
                .filter(follower -> !Boolean.TRUE.equals(follower.getFollower().getIsDeleted()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<FollowerResponse> findFollowingOf(Long userId) {
        userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new UserNotFoundException("User with " + userId + " not found"));
        return followerRepository.findByFollowerId(userId).stream()
                .filter(follower -> !Boolean.TRUE.equals(follower.getFollowing().getIsDeleted()))
                .map(this::toResponse)
                .toList();
    }
}
