package com.example.twitter_challenge.Service;

/** Small seam around the Google GenAI SDK, which is difficult to mock directly. */
public interface GeminiGateway {
    String generate(String model, String prompt);
}
