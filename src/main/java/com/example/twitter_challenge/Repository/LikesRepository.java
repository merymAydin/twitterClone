package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Likes;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikesRepository extends JpaRepository<Likes, Long> {
    Optional<Likes> findByTweetIdAndUserId(Long tweetId, Long userId);
}
