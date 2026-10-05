package com.example.twitter_challenge;


import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.GeminiServiceImpl;
import com.google.genai.Client;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.google.genai.types.GenerateContentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GeminiServiceImplTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Client client;
    @Mock
    private TweetRepository tweetRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GenerateContentResponse response;

    @InjectMocks
    private GeminiServiceImpl geminiServiceImpl;


    @Test
    void chat_shouldReturnResponse() {
        when(client.models.generateContent(
                "gemini-3.8-flash",
                "Hello",
                null
        )).thenReturn(response);
        when(response.text()).thenReturn("Hello from Gemini");
        String result = geminiServiceImpl.chat("hello");
        assertEquals("Hello from Gemini", result);
    }
}
