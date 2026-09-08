package com.example.twitter_challenge.dto.request;

import com.example.twitter_challenge.Utils.Commons.Location;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


public record CreateTweetRequest( @NotBlank String content,@NotNull Long userId, Location location, Long parentId) {
}
