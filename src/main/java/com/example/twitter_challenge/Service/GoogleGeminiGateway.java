package com.example.twitter_challenge.Service;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ThinkingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * The Google GenAI implementation of {@link GeminiGateway}.
 *
 * <p>Every request uses the streaming API of the SDK, also for the callers that need the
 * complete answer: the chunks arrive one after another instead of the answer being buffered by
 * the model server, and a stalled stream fails on the configured request timeout instead of
 * holding the request thread.</p>
 *
 * <p>The generation settings are configuration driven so they can be tuned without a code
 * change:</p>
 * <ul>
 *   <li>{@code gemini.thinking-budget} - the reasoning budget in tokens. {@code 0} disables the
 *       reasoning, which is what makes the short tweet assistant answers fast.</li>
 *   <li>{@code gemini.max-output-tokens} - a guard against runaway generations. {@code 0} means
 *       no limit.</li>
 * </ul>
 */
@Slf4j
@Component
public class GoogleGeminiGateway implements GeminiGateway {

    private final Client client;

    @Value("${gemini.thinking-budget:0}")
    private int thinkingBudget;

    @Value("${gemini.max-output-tokens:2048}")
    private int maxOutputTokens;

    public GoogleGeminiGateway(Client client) {
        this.client = client;
    }

    @Override
    public String generate(String model, String prompt) {
        StringBuilder answer = new StringBuilder();
        generateStreaming(model, prompt, answer::append);
        return answer.toString();
    }

    @Override
    public void generateStreaming(String model, String prompt, Consumer<String> chunkConsumer) {
        long startedAt = System.nanoTime();
        int chunks = 0;
        try (ResponseStream<GenerateContentResponse> stream =
                     client.models.generateContentStream(model, prompt, generationConfig())) {
            for (GenerateContentResponse response : stream) {
                String text = response.text();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                chunks++;
                chunkConsumer.accept(text);
            }
        }
        log.info(
                "Gemini API call finished: model={}, thinkingBudget={}, chunks={}, durationMs={}",
                model,
                thinkingBudget,
                chunks,
                elapsedMillis(startedAt)
        );
    }

    private GenerateContentConfig generationConfig() {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder();
        if (thinkingBudget >= 0) {
            builder.thinkingConfig(
                    ThinkingConfig.builder()
                            .thinkingBudget(thinkingBudget)
                            .includeThoughts(false)
                            .build()
            );
        }
        if (maxOutputTokens > 0) {
            builder.maxOutputTokens(maxOutputTokens);
        }
        return builder.build();
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
