package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.GeminiService;
import com.example.twitter_challenge.exception.GeminiException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeminiServiceImpl implements GeminiService {

    private final UserRepository userRepository;
    private Client client;
    private TweetRepository tweetRepository;
    private final static Logger logger = LoggerFactory.getLogger(GeminiServiceImpl.class);


    public GeminiServiceImpl(Client client, TweetRepository tweetRepository, UserRepository userRepository){
        this.client = client;
        this.tweetRepository = tweetRepository;
        this.userRepository = userRepository;
    }

    private String generate(String prompt) {

        try{
            String response = client.models.generateContent(
                    "gemini-2.5-flash",
                    prompt,
                    null
            ).text();
            return response;
        }catch (Exception e) {
            logger.error("Gemini service is currently unavailable",e);
            throw new GeminiException("Gemini service is currently unavailable");
        }

    }



    @Override
    public String chat(String message) {
        String output = generate(message);
         return output;

    }

    @Override
    public String rewriteTweet(Long tweetId, String tone){
        Tweet tweet = tweetRepository.findById(tweetId).orElseThrow(()->new TweetNotFoundException("Tweet not found"));

        String prompt =
                "Rewrite this tweet in a\n" + tone + "tone\n"
                        + "Preserve the original meaning.\n"
                        + "Tweet:\n" + tweet.getContent();
        String output = generate(prompt);
        return output;
    }

    @Override
    public String rewriteDraft(String content, String tone) {
        String prompt =
                "Rewrite this draft  in a\n" + tone + "tone\n"
                        + "Preserve the original meaning.\n"
                        + "Tweet:\n" + content;
        String output = generate(prompt);
        return output;
    }

    @Override
    public String summarizeTweet(String content) {
        String prompt =
                "Summarize the following tweet briefly. Keep its main idea.\n"
                        + "Tweet:\n" + content;
        String output = generate(prompt);
        return output;
    }

    @Override
    public String analyzeUserTweets() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUserName(username).orElseThrow(()->new UserNotFoundException("user not found"));
        Page<Tweet> tweets = tweetRepository.findByUserId(user.getId(),Pageable.ofSize(20));

        if (tweets.isEmpty()) {
            throw new TweetNotFoundException("Tweets not found");
        }
        StringBuilder tweetsContent = new StringBuilder();

        for (Tweet tweet : tweets.getContent()) {
            tweetsContent.append(tweet.getContent()).append("\n");
        }

        String prompt =
                "Analyze the following tweets from this user briefly.\n"
                        + "Tweet:\n" + tweetsContent;
        String output = generate(prompt);
        return output;
    }
}
