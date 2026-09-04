package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Integer> {
    Optional<Bookmark> findByTweetIdAndUserId(Long tweetId, Long userId);
}
