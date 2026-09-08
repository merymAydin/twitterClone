package com.example.twitter_challenge.dto.request;

import com.example.twitter_challenge.Utils.Commons.Location;
import jakarta.validation.constraints.NotBlank;

public record UpdateTweetRequest(@NotBlank String content) {
}
