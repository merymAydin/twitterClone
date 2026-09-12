package com.example.twitter_challenge.exception;

import com.example.twitter_challenge.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Date;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException exception) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                new Date()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);

    }
    @ExceptionHandler(TweetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTweetNotFound(TweetNotFoundException exception) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND,exception.getMessage(),new Date());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(LikeAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleLikeAlreadyExists(LikeAlreadyExistsException  exception) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.CONFLICT,exception.getMessage(),new Date());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(BookmarkAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleBookmarkAlreadyExists(BookmarkAlreadyExistsException exception) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.CONFLICT,exception.getMessage(),new Date());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(FollowerAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleFollowerAlreadyExists(FollowerAlreadyExistsException exception) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.CONFLICT,exception.getMessage(),new Date());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }
    @ExceptionHandler(LikeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLikeNotFound(LikeNotFoundException exception) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND,exception.getMessage(),new Date());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(BookmarkNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBookmarkNotFound(BookmarkNotFoundException exception) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND,exception.getMessage(),new Date());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(FollowerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFollowerNotFound(FollowerNotFoundException exception) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND,exception.getMessage(),new Date());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    @ExceptionHandler(CommentNotFound.class)
    public ResponseEntity<ErrorResponse> handleCommentNotFound(CommentNotFound exception) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND,exception.getMessage(),new Date());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);

    }
    @ExceptionHandler(StatisticsNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStatisticsNotFound(StatisticsNotFoundException exception) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND,exception.getMessage(),new Date());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);

    }
    @ExceptionHandler(FollowerSelfFollowException.class)
    public ResponseEntity<ErrorResponse> handleFollowerSelfFollow(FollowerSelfFollowException exception) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST,exception.getMessage(),new Date());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);

    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException exception) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.FORBIDDEN,exception.getMessage(),new Date());
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult()
                .getFieldErrors()
                .get(0);
        String message = fieldError.getField()
                + ": "
                + fieldError.getDefaultMessage();

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                message,
                new Date()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
}
