package com.example.twitter_challenge.Service.interfaces;


import com.example.twitter_challenge.dto.request.GeminiRewriteRequest;

public interface GeminiService {
    String chat(String message);
    String rewriteTweet(Long tweetId, String tone);
    String rewriteDraft(String content, String tone);
    String summarizeTweet(String content);
    String analyzeUserTweets();
}
