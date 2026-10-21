package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.dto.request.LoginRequest;
import com.example.twitter_challenge.dto.response.LoginResponse;
import com.example.twitter_challenge.security.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String email = request.email().trim();
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
            if (!authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            // Use the stored canonical email, not the caller-provided spelling, as the JWT subject.
            return ResponseEntity.ok(new LoginResponse(jwtService.generateToken(authentication.getName())));
        } catch (AuthenticationException exception) {
            // Do not reveal whether the email exists or the password was wrong.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
