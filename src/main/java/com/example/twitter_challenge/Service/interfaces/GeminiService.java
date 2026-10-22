package com.example.twitter_challenge.Service.interfaces;


import java.util.function.Consumer;

public interface GeminiService {
    String chat(String message);

    /**
     * Answers a chat message and hands every produced chunk to the consumer as soon as it
     * arrives, so the caller can deliver the answer while it is still being written.
     */
    void chatStreaming(String message, Consumer<String> chunkConsumer);

    String rewriteTweet(Long tweetId, String tone);
    String rewriteDraft(String content, String tone);
    String summarizeTweet(String content);
    String analyzeUserTweets();
}
