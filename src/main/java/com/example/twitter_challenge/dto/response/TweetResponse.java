package com.example.twitter_challenge.dto.response;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Utils.Commons.Location;

public record TweetResponse (String content, Long userId, Location location, Long parentId, StatisticsResponse statistics) {

}
