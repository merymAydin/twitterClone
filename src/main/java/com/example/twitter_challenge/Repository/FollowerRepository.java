package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Follower;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FollowerRepository extends JpaRepository<Follower,Long> {
    Optional<Follower> findByFollowerIdAndFollowingId(Long followerId, Long followingId);
}
