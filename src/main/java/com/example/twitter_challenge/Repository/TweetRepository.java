package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Tweet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TweetRepository extends JpaRepository<Tweet, Long> {
    Page<Tweet> findByUserIdIn(List<Long> userIds, Pageable pageable);

    Page<Tweet> findByContentContaining(String keyword, Pageable pageable);
}
