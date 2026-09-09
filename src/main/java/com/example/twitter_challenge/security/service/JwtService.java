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
    private SecretKey secretKey;
    @PostConstruct
    public void init() {
        byte[] key = Decoders.BASE64.decode(secret);
        secretKey = Keys.hmacShaKeyFor(key);
    }


    public String generateToken(String username){

        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now()
                        .plusSeconds(3600)))
                .signWith(secretKey).compact();
    }

    public String extractUsername(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload().getSubject();
    }
}
