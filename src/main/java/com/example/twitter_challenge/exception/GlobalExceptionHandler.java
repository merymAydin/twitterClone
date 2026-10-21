package com.example.twitter_challenge.exception;

import com.example.twitter_challenge.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Date;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(TweetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTweetNotFound(TweetNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({
            LikeNotFoundException.class,
            BookmarkNotFoundException.class,
            FollowerNotFoundException.class,
            CommentNotFound.class,
            StatisticsNotFoundException.class,
            NotificationNotFoundException.class,
            SubscriptionNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({
            LikeAlreadyExistsException.class,
            BookmarkAlreadyExistsException.class,
            FollowerAlreadyExistsException.class,
            UserAlreadyExistsException.class,
            ActiveSubscriptionExistsException.class,
            DataIntegrityViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException exception) {
        String message = exception instanceof DataIntegrityViolationException
                ? "The request conflicts with an existing resource"
                : exception.getMessage();
        return error(HttpStatus.CONFLICT, message);
    }

    @ExceptionHandler(FollowerSelfFollowException.class)
    public ResponseEntity<ErrorResponse> handleFollowerSelfFollow(FollowerSelfFollowException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException exception) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .orElse(null);
        String message = fieldError == null
                ? "Request validation failed"
                : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        String message = exception instanceof IllegalArgumentException
                ? exception.getMessage()
                : "Malformed request";
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException exception) {
        // State errors can contain Stripe identifiers or other internal details. Log the cause,
        // but do not return it to the client.
        log.warn("Request failed due to an invalid application state", exception);
        return error(HttpStatus.CONFLICT, "The request cannot be completed in the current state");
    }

    @ExceptionHandler(GeminiException.class)
    public ResponseEntity<ErrorResponse> handleGeminiException(GeminiException exception) {
        return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(StripeCustomerException.class)
    public ResponseEntity<ErrorResponse> handleStripeCustomerException(StripeCustomerException exception) {
        // The service only puts safe reconciliation guidance in this exception. Never replace it
        // with the generic IllegalStateException response: the client needs to know whether to
        // complete authentication, attach a payment method, or contact support.
        log.warn("Stripe customer state conflict: {}", exception.getMessage());
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(StripeConfigurationException.class)
    public ResponseEntity<ErrorResponse> handleStripeConfigurationException(
            StripeConfigurationException exception
    ) {
        log.error("Stripe configuration is not usable: {}", exception.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(StripeOperationException.class)
    public ResponseEntity<ErrorResponse> handleStripeOperationException(StripeOperationException exception) {
        return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return new ResponseEntity<>(
                new ErrorResponse(status, message, new Date()),
                status
        );
    }
}
