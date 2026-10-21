package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Statistics;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StatisticsRepository extends JpaRepository<Statistics, Long> {

    Optional<Statistics> findByTweetId(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Statistics s where s.tweet.id = :tweetId")
    Optional<Statistics> findByTweetIdForUpdate(@Param("tweetId") Long tweetId);
}
