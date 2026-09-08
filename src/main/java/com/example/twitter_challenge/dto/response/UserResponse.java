package com.example.twitter_challenge.dto.response;

import com.example.twitter_challenge.Utils.Commons.Location;

import java.util.Date;

public record UserResponse(Long userId, String username, String email, Date birthday, Location location, String bio, String photo, String banner) {
}