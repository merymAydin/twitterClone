package com.example.twitter_challenge.exception;

public class LikeNotFoundException extends RuntimeException{
    public LikeNotFoundException(String message) {
        super(message);
    }
}