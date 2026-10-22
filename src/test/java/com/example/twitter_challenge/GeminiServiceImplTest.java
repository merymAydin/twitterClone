package com.example.twitter_challenge;

import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.GeminiGateway;
import com.example.twitter_challenge.Service.GeminiServiceImpl;
import com.example.twitter_challenge.exception.GeminiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class GeminiServiceImplTest {
    @Mock
    private GeminiGateway geminiGateway;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GeminiServiceImpl geminiService;

    @Test
    void chat_shouldReturnResponse() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-key");
        ReflectionTestUtils.setField(geminiService, "model", "gemini-3.8-flash");
        doAnswer(invocation -> {
            Consumer<String> chunkConsumer = invocation.getArgument(2);
            chunkConsumer.accept("Hello from ");
            chunkConsumer.accept("Gemini");
            return null;
        }).when(geminiGateway).generateStreaming(eq("gemini-3.8-flash"), eq("Hello"), any());

        String result = geminiService.chat("Hello");

        assertEquals("Hello from Gemini", result);
    }

    @Test
    void chatStreaming_shouldHandEveryChunkToTheConsumerAsItArrives() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-key");
        ReflectionTestUtils.setField(geminiService, "model", "gemini-3.8-flash");
        doAnswer(invocation -> {
            Consumer<String> chunkConsumer = invocation.getArgument(2);
            chunkConsumer.accept("Hi ");
            chunkConsumer.accept("there");
            return null;
        }).when(geminiGateway).generateStreaming(eq("gemini-3.8-flash"), eq("Hello"), any());

        List<String> received = new ArrayList<>();
        geminiService.chatStreaming("Hello", received::add);

        assertEquals(List.of("Hi ", "there"), received);
    }

    @Test
    void chat_shouldFailWhenTheModelProducesNothing() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-key");
        ReflectionTestUtils.setField(geminiService, "model", "gemini-3.8-flash");

        GeminiException exception = assertThrows(
                GeminiException.class,
                () -> geminiService.chat("Hello")
        );

        assertEquals("Gemini returned an empty response", exception.getMessage());
    }

    @Test
    void chat_shouldFailMeaningfullyWhenApiKeyIsMissing() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "");

        GeminiException exception = assertThrows(
                GeminiException.class,
                () -> geminiService.chat("Hello")
        );

        assertEquals("Gemini is not configured", exception.getMessage());
    }

    @Test
    void chat_shouldRejectBlankPrompt() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-key");

        assertThrows(IllegalArgumentException.class, () -> geminiService.chat(" "));
    }
}
