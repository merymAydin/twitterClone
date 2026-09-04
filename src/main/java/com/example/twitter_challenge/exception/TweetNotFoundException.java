package com.example.twitter_challenge.exception;

public class TweetNotFoundException extends RuntimeException{
    public TweetNotFoundException(String message) {
        super(message);
    }
}
