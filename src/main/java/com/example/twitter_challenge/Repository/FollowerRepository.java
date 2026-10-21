package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Follower;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowerRepository extends JpaRepository<Follower, Long> {

    Optional<Follower> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    @EntityGraph(attributePaths = {"follower", "following"})
    List<Follower> findByFollowerId(Long followerId);

    @EntityGraph(attributePaths = {"follower", "following"})
    List<Follower> findByFollowingId(Long followingId);
}
