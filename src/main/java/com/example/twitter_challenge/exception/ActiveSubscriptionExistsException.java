package com.example.twitter_challenge.exception;

public class ActiveSubscriptionExistsException extends RuntimeException {

    public ActiveSubscriptionExistsException(String message) {
        super(message);
    }
}
