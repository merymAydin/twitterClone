package com.example.twitter_challenge.dto.request;


import jakarta.validation.constraints.NotNull;

public record CreateFollowerRequest(@NotNull Long followerId, @NotNull Long followingId){

}
