package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Bookmark;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByTweetIdAndUserId(Long tweetId, Long userId);

    Optional<Bookmark> findByTweetIdAndUserIdAndIsDeletedFalse(Long tweetId, Long userId);

    Optional<Bookmark> findByIdAndUserIdAndIsDeletedFalse(Long id, Long userId);

    @EntityGraph(attributePaths = {"tweet", "tweet.user", "tweet.statistics"})
    List<Bookmark> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(Long userId);
}
