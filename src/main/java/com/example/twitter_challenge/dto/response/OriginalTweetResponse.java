package com.example.twitter_challenge.dto.response;

/**
 * The original tweet behind a retweet or a quote.
 *
 * <p>A retweet and a quote are stored as a tweet with a parent reference. Without the parent
 * information in the response a client cannot show whose tweet was retweeted or quoted, and a
 * retweet cannot be rendered at all because it carries no content of its own.</p>
 *
 * <p>{@code premium} is the badge of the original author and is derived by the backend from the
 * persisted subscription state, like every other premium flag.</p>
 */
public record OriginalTweetResponse(
        Long id,
        String content,
        Long userId,
        String authorUsername,
        String authorPhoto,
        boolean premium,
        StatisticsResponse statistics
) {
}
