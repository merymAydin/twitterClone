package com.example.twitter_challenge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<?> handleUserNotFound(UserNotFoundException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(TweetNotFoundException.class)
    public ResponseEntity<?> handleTweetNotFound(TweetNotFoundException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(LikeAlreadyExistsException.class)
    public ResponseEntity<?> handleLikeFound(LikeAlreadyExistsException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.CONFLICT);
    }
    @ExceptionHandler(BookmarkAlreadyExistsException.class)
    public ResponseEntity<?> handleLikeFound(BookmarkAlreadyExistsException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.CONFLICT);
    }
    @ExceptionHandler(FollowerAlreadyExistsException.class)
    public ResponseEntity<?> handleFollowerFound(FollowerAlreadyExistsException exception) {
        return new ResponseEntity<>(exception.getMessage(), HttpStatus.CONFLICT);
    }
}
