package com.example.twitter_challenge.exception;

public class BookmarkNotFoundException extends RuntimeException{
    public BookmarkNotFoundException(String message) {
        super(message);
    }
}
