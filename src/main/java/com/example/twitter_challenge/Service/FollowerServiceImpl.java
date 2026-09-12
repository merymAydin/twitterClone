package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Follower;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.FollowerRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.FollowerService;
import com.example.twitter_challenge.dto.request.CreateFollowerRequest;
import com.example.twitter_challenge.dto.response.BookmarkResponse;
import com.example.twitter_challenge.dto.response.FollowerResponse;
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

    public FollowerServiceImpl(FollowerRepository followerRepository, UserRepository userRepository) {
        this.followerRepository = followerRepository;
        this.userRepository = userRepository;
    }

    @Override
    public FollowerResponse createFollower(CreateFollowerRequest request) {
        if (request.followerId().equals(request.followingId())) {
            throw new FollowerSelfFollowException(
                    "User " + request.followerId() + " cannot follow themselves"
            );
        }

        Optional<Follower> existingFollower = followerRepository.findByFollowerIdAndFollowingId(
                request.followerId(),
                request.followingId()
        );
        if (existingFollower.isPresent()) {
            throw new FollowerAlreadyExistsException(
                    "User " + request.followerId() + " has already followed user " + request.followingId()
            );
        }
        User followingUser = userRepository.findById(request.followingId())
                .orElseThrow(() ->
                        new UserNotFoundException("User with " + request.followingId() + " not found")
                );
        User followerUser = userRepository.findById(request.followerId())
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "User with " + request.followerId() + " not found"
                        )
                );

        Follower follower = new Follower(followerUser,followingUser);
        Follower savedFollower = followerRepository.save(follower);
        return new FollowerResponse(savedFollower.getFollower().getId(), savedFollower.getFollowing().getId());
    }


    @Transactional
    @Override
    public void removeFollower(CreateFollowerRequest  request) {
        Follower follower = followerRepository.findByFollowerIdAndFollowingId(request.followerId(),request.followingId()).orElseThrow(
                ()->new FollowerNotFoundException(
                        "Follower relationship between user "
                                + request.followerId()
                                + " and user "
                                + request.followingId()
                                + " not found"
                )
        );
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!currentUsername.equals(follower.getFollower().getUserName())){
            throw new ForbiddenException("You are not allowed to remove this follower");
        }
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
