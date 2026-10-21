package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Likes;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikesRepository extends JpaRepository<Likes, Long> {

    Optional<Likes> findByTweetIdAndUserId(Long tweetId, Long userId);

    Optional<Likes> findByTweetIdAndUserIdAndIsDeletedFalse(Long tweetId, Long userId);

    Optional<Likes> findByIdAndIsDeletedFalse(Long id);

    @Override
    @EntityGraph(attributePaths = {"tweet", "user"})
    List<Likes> findAll();
}
