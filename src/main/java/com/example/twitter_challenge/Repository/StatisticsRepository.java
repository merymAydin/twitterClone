package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Statistics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatisticsRepository extends JpaRepository<Statistics, Long> {
}
