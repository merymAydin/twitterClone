package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.GeminiService;
import com.example.twitter_challenge.dto.request.GeminiRewriteRequest;
import com.example.twitter_challenge.exception.GeminiException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/gemini")
public class GeminiController {

    private final GeminiService geminiService;

    public GeminiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody String message){
        long startedAt = System.nanoTime();
        String answer = geminiService.chat(message);
        log.info("Gemini endpoint /chat finished: durationMs={}", (System.nanoTime() - startedAt) / 1_000_000L);
        return answer;
    }

    /**
     * The same chat as {@link #chat(String)}, but the answer is delivered as it is generated.
     *
     * <p>Every chunk of the answer is written as a server sent event and flushed immediately, so
     * a client can show the answer while the model is still writing it. The endpoint writes to
     * the response directly instead of using an async {@code SseEmitter}: the request stays on
     * the authenticated thread, which keeps the existing security setup untouched. Errors that
     * happen before the first chunk (bad request, missing configuration, API failure) are still
     * translated into the usual HTTP errors by the global exception handler.</p>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void chatStream(@RequestBody String message, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer = response.getWriter();
        long startedAt = System.nanoTime();
        try {
            geminiService.chatStreaming(message, chunk -> {
                writer.print("data: ");
                writer.print(chunk.replace("\r\n", "\n").replace("\n", "\ndata: "));
                writer.print("\n\n");
                writer.flush();
            });
        } catch (GeminiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Gemini streaming was interrupted: {}", exception.getMessage());
            throw new GeminiException("The client closed the connection");
        }
        writer.flush();
        log.info(
                "Gemini endpoint /chat/stream finished: durationMs={}",
                (System.nanoTime() - startedAt) / 1_000_000L
        );
    }

    @PostMapping("/rewrite/{tweetId}")
    public String rewriteTweet(
            @PathVariable Long tweetId,
            @Valid @RequestBody GeminiRewriteRequest request
    ) {
        long startedAt = System.nanoTime();
        String answer = geminiService.rewriteTweet(tweetId, request.tone());
        log.info(
                "Gemini endpoint /rewrite/{} finished: durationMs={}",
                tweetId,
                (System.nanoTime() - startedAt) / 1_000_000L
        );
        return answer;
    }

    @PostMapping("/rewriteDraft")
    public String rewriteDraft(@Valid @RequestBody GeminiRewriteRequest request) {
        long startedAt = System.nanoTime();
        String answer = geminiService.rewriteDraft(request.content(), request.tone());
        log.info(
                "Gemini endpoint /rewriteDraft finished: durationMs={}",
                (System.nanoTime() - startedAt) / 1_000_000L
        );
        return answer;
    }

    @PostMapping("/summarizeTweet")
    public String summarizeTweet(@RequestBody String content){
        long startedAt = System.nanoTime();
        String answer = geminiService.summarizeTweet(content);
        log.info(
                "Gemini endpoint /summarizeTweet finished: durationMs={}",
                (System.nanoTime() - startedAt) / 1_000_000L
        );
        return answer;
    }

    @PostMapping("/analyzeUserTweets")
    public String analyzeUserTweets(){
        long startedAt = System.nanoTime();
        String answer = geminiService.analyzeUserTweets();
        log.info(
                "Gemini endpoint /analyzeUserTweets finished: durationMs={}",
                (System.nanoTime() - startedAt) / 1_000_000L
        );
        return answer;
    }
}
