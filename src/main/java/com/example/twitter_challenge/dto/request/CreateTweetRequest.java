package com.example.twitter_challenge.dto.request;

import com.example.twitter_challenge.Utils.Commons.Location;



public record CreateTweetRequest(String content, Long userId, Location location,Long parentId) {
}
