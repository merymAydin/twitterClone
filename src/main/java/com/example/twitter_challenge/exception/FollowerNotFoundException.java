package com.example.twitter_challenge.exception;

public class FollowerNotFoundException extends RuntimeException{
    public FollowerNotFoundException(String message) {
        super(message);
    }
}
