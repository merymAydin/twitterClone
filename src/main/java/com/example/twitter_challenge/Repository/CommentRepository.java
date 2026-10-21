package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = {"user", "tweet"})
    List<Comment> findByTweetIdAndIsDeletedFalseOrderByCreatedAtAsc(Long tweetId);

    @EntityGraph(attributePaths = {"user", "tweet", "tweet.user"})
    Optional<Comment> findByIdAndIsDeletedFalse(Long id);

    @Override
    @EntityGraph(attributePaths = {"user", "tweet"})
    List<Comment> findAll();
}
