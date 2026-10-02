package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.GeminiService;
import com.example.twitter_challenge.dto.request.GeminiRewriteRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gemini")
public class GeminiController {

    private final GeminiService geminiService;

    public GeminiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody String message){
        return geminiService.chat(message);
    }

    @PostMapping("/rewrite/{tweetId}")
    public GeminiRewriteRequest<String> rewriteTweet(@PathVariable Long tweetId, @RequestBody String tone){
        return geminiService.rewriteTweet(tweetId,tone);
    }

    @PostMapping("/rewriteDraft")
    public String rewriteDraft(@RequestBody String content,String tone){
        return geminiService.rewriteDraft(content,tone);
    }

    @PostMapping("/summarizeTweet")
    public String summarizeTweet(@RequestBody String content){
        return geminiService.summarizeTweet(content);
    }

    @PostMapping("/analyzeUserTweets")
    public String analyzeUserTweets(){
        return geminiService.analyzeUserTweets();
    }
}
