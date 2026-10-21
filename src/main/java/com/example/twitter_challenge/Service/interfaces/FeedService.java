package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.dto.response.TweetResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FeedService {

    /**
     * Home timeline: the tweets of the signed-in user and of the users they follow.
     */
    Page<TweetResponse> getTweets(Long userId,Pageable pageable);

    /**
     * Explore feed: every visible tweet of every active user, newest first.
     *
     * <p>Independent of the follow graph: tweets of users the caller does not follow are
     * included. The ordering is fixed to newest first, so a client supplied sort is ignored
     * to keep the pagination deterministic.</p>
     */
    Page<TweetResponse> getExploreTweets(Pageable pageable);
}
