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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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
        when(geminiGateway.generate("gemini-3.8-flash", "Hello"))
                .thenReturn("Hello from Gemini");

        String result = geminiService.chat("Hello");

        assertEquals("Hello from Gemini", result);
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
