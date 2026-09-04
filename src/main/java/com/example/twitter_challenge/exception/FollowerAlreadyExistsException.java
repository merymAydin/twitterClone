package com.example.twitter_challenge.exception;

public class FollowerAlreadyExistsException extends RuntimeException{
    public FollowerAlreadyExistsException(String message) {
        super(message);
    }
}
