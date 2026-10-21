package com.example.twitter_challenge.security.config;

import com.google.genai.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class GeminiConfig {

    @Value("${gemini.api-key:}")
    private String apiKey;

    /**
     * The bean must never be null: Spring cannot inject a null bean. When the key is absent the
     * application still starts, but GeminiServiceImpl refuses to call the API and returns a
     * meaningful 502 instead of throwing a NullPointerException.
     */
    @Bean
    public Client geminiClient() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY is not configured; Gemini endpoints will return 502");
            return new Client.Builder().apiKey("not-configured").build();
        }
        return new Client.Builder().apiKey(apiKey).build();
    }
}
