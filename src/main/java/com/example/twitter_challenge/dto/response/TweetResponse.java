package com.example.twitter_challenge.dto.response;

import com.example.twitter_challenge.Utils.Commons.Location;

/**
 * A tweet as returned by the tweet and feed endpoints.
 *
 * <p>{@code authorUsername} and {@code authorPhoto} carry the author information so a feed can
 * be rendered without a second lookup. {@code premium} is the author's premium badge and is
 * derived by the backend from the persisted subscription state, exactly like the {@code premium}
 * field of the user profile DTOs. It is never taken from client input.</p>
 *
 * <p>{@code originalTweet} is present for a retweet and for a quote: it describes the tweet that
 * was retweeted or quoted, including its author. It is {@code null} for an ordinary tweet.</p>
 */
public record TweetResponse (
        Long id,
        String content,
        Long userId,
        String authorUsername,
        String authorPhoto,
        boolean premium,
        Location location,
        Long parentId,
        OriginalTweetResponse originalTweet,
        StatisticsResponse statistics
) {

}
