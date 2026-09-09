package com.example.twitter_challenge.security.service;

import java.security.SecureRandom;
import java.util.Base64;

public class SecretGenerator {
    public static void main(String[] args){
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String encoded = Base64.getEncoder().encodeToString(randomBytes);
        System.out.println(encoded);

    }
}

