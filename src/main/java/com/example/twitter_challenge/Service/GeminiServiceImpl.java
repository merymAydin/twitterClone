package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.GeminiService;
import com.example.twitter_challenge.exception.GeminiException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class GeminiServiceImpl implements GeminiService {

    private final UserRepository userRepository;
    private final GeminiGateway geminiGateway;
    private final TweetRepository tweetRepository;
    private static final Logger logger = LoggerFactory.getLogger(GeminiServiceImpl.class);

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String model = "gemini-3.8-flash";

    public GeminiServiceImpl(
            GeminiGateway geminiGateway,
            TweetRepository tweetRepository,
            UserRepository userRepository
    ) {
        this.geminiGateway = geminiGateway;
        this.tweetRepository = tweetRepository;
        this.userRepository = userRepository;
    }

    private String generate(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeminiException("Gemini is not configured");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be blank");
        }

        try {
            String output = geminiGateway.generate(model, prompt);
            if (output == null || output.isBlank()) {
                throw new GeminiException("Gemini returned an empty response");
            }
            return output;
        } catch (GeminiException exception) {
            throw exception;
        } catch (Exception exception) {
            // Do not log the prompt: it can contain private user content.
            logger.error("Gemini service is currently unavailable", exception);
            throw new GeminiException("Gemini service is currently unavailable");
        }
    }

    @Override
    public String chat(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message must not be blank");
        }
        if (message.length() > 5000) {
            throw new IllegalArgumentException("Message is too long");
        }
        return generate(message);
    }

    @Override
    public String rewriteTweet(Long tweetId, String tone) {
        Tweet tweet = tweetRepository.findVisibleById(tweetId)
                .orElseThrow(() -> new TweetNotFoundException("Tweet not found"));
        if (tweet.getContent() == null) {
            throw new IllegalArgumentException("Retweets cannot be rewritten");
        }
        String prompt = "Rewrite this tweet in a " + safeTone(tone) + " tone. "
                + "Preserve the original meaning.\nTweet:\n" + tweet.getContent();
        return generate(prompt);
    }

    @Override
    public String rewriteDraft(String content, String tone) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content must not be blank");
        }
        if (content.length() > 5000) {
            throw new IllegalArgumentException("Content is too long");
        }
        String prompt = "Rewrite this draft in a " + safeTone(tone) + " tone. "
                + "Preserve the original meaning.\nTweet:\n" + content;
        return generate(prompt);
    }

    @Override
    public String summarizeTweet(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content must not be blank");
        }
        if (content.length() > 5000) {
            throw new IllegalArgumentException("Content is too long");
        }
        String prompt = "Summarize the following tweet briefly. Keep its main idea.\nTweet:\n" + content;
        return generate(prompt);
    }

    @Override
    public String analyzeUserTweets() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User user = userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        Page<Tweet> tweets = tweetRepository.findVisibleByUserId(user.getId(), Pageable.ofSize(20));

        if (tweets.isEmpty()) {
            throw new TweetNotFoundException("Tweets not found");
        }
        StringBuilder tweetsContent = new StringBuilder();
        for (Tweet tweet : tweets.getContent()) {
            if (tweet.getContent() != null) {
                tweetsContent.append(tweet.getContent()).append('\n');
            }
        }

        String prompt = "Analyze the following tweets from this user briefly.\nTweets:\n" + tweetsContent;
        return generate(prompt);
    }

    private String safeTone(String tone) {
        if (tone == null || tone.isBlank()) {
            throw new IllegalArgumentException("Tone must not be blank");
        }
        return tone.replaceAll("[\\r\\n]", " ").trim();
    }
}
