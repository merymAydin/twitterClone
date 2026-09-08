package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.dto.response.StatisticsResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatisticsRepository extends JpaRepository<Statistics, Long> {
    Optional<Statistics> findByTweetId(Long id);}
