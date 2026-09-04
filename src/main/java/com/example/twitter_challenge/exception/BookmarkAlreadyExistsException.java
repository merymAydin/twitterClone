package com.example.twitter_challenge.exception;

public class BookmarkAlreadyExistsException extends RuntimeException{
    public BookmarkAlreadyExistsException(String message) {
        super(message);
    }
}
