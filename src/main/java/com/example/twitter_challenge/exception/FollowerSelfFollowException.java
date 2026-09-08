package com.example.twitter_challenge.exception;

public class FollowerSelfFollowException extends RuntimeException{
    public FollowerSelfFollowException(String message){
        super(message);
    }
}
