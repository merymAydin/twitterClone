package com.example.twitter_challenge.dto.response;

import org.springframework.http.HttpStatus;

import java.util.Date;

public record ErrorResponse(HttpStatus status, String message, Date timestamp) {
}
