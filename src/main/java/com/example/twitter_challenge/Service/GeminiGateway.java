package com.example.twitter_challenge.Service;

import java.util.function.Consumer;

/** Small seam around the Google GenAI SDK, which is difficult to mock directly. */
public interface GeminiGateway {

    /**
     * Generates the answer and returns it completely.
     */
    String generate(String model, String prompt);

    /**
     * Generates the answer and hands every produced chunk to the consumer as soon as it
     * arrives, so the caller can deliver it without waiting for the whole answer.
     */
    void generateStreaming(String model, String prompt, Consumer<String> chunkConsumer);
}
