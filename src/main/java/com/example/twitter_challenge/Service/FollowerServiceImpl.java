package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Follower;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.FollowerRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.dto.request.CreateFollowerRequest;
import com.example.twitter_challenge.dto.response.FollowerResponse;
import com.example.twitter_challenge.exception.FollowerAlreadyExistsException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

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
                                "User with " + request.followingId() + " not found"
                        )
                );
        Follower follower = new Follower(followerUser,followingUser);
        Follower savedFollower = followerRepository.save(follower);
        return new FollowerResponse(savedFollower.getFollower().getId(), savedFollower.getFollowing().getId());
    }
}
