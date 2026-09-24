package com.example.twitter_challenge.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateLikeRequest( @NotNull Long tweetId){
}
