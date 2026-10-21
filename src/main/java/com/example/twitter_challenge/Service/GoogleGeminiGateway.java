package com.example.twitter_challenge.Service;

import com.google.genai.Client;
import org.springframework.stereotype.Component;

@Component
public class GoogleGeminiGateway implements GeminiGateway {

    private final Client client;

    public GoogleGeminiGateway(Client client) {
        this.client = client;
    }

    @Override
    public String generate(String model, String prompt) {
        return client.models.generateContent(model, prompt, null).text();
    }
}
