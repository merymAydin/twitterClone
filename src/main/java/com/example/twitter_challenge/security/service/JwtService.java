package com.example.twitter_challenge.security.service;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.time.Instant;

import java.util.Date;

@Service
public class JwtService {




    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-seconds:3600}")
    private long expirationSeconds;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured");
        }
        try {
            byte[] key = Decoders.BASE64.decode(secret);
            secretKey = Keys.hmacShaKeyFor(key);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET must be a Base64-encoded key of at least 256 bits",
                    exception
            );
        }
    }


    public String generateToken(String email){

        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now()
                        .plusSeconds(expirationSeconds)))
                .signWith(secretKey).compact();
    }

    public String extractEmail(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload().getSubject();
    }


}
