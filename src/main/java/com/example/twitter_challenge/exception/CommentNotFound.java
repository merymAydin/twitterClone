package com.example.twitter_challenge.exception;

public class CommentNotFound extends RuntimeException{
    public CommentNotFound(String message){
        super(message);
    }
}
