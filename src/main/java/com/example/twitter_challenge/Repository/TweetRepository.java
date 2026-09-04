package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Tweet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TweetRepository extends JpaRepository<Tweet, Long> {
}
