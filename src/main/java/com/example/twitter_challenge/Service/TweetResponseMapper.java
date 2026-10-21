package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.example.twitter_challenge.dto.response.OriginalTweetResponse;
import com.example.twitter_challenge.dto.response.StatisticsResponse;
import com.example.twitter_challenge.dto.response.TweetResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds {@link TweetResponse} objects for every endpoint that returns tweets.
 *
 * <p>The premium badge of the author and of the original author are resolved from the persisted
 * subscription state through {@link SubscriptionService}. For a page of tweets the authors are
 * resolved with a single query, so a feed never degrades into an N+1 query pattern.</p>
 */
@Component
public class TweetResponseMapper {

    private final SubscriptionService subscriptionService;

    public TweetResponseMapper(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Maps a single tweet. The premium flags cost one extra query, which is acceptable for a
     * single resource.
     */
    public TweetResponse toResponse(Tweet tweet) {
        if (tweet == null) {
            return null;
        }
        Set<Long> premiumUserIds = premiumUserIds(List.of(tweet));
        return toResponse(tweet, isPremium(tweet.getUser(), premiumUserIds), premiumUserIds);
    }

    /**
     * Maps a page of tweets. The premium flags of all authors and original authors are resolved
     * with one query for the whole page.
     */
    public Page<TweetResponse> toResponsePage(Page<Tweet> tweets) {
        if (tweets == null) {
            return Page.empty();
        }
        Set<Long> premiumUserIds = premiumUserIds(tweets.getContent());
        return tweets.map(tweet -> toResponse(
                tweet,
                isPremium(tweet.getUser(), premiumUserIds),
                premiumUserIds
        ));
    }

    private TweetResponse toResponse(Tweet tweet, boolean authorPremium, Set<Long> premiumUserIds) {
        User author = tweet.getUser();
        return new TweetResponse(
                tweet.getId(),
                tweet.getContent(),
                author == null ? null : author.getId(),
                author == null ? null : author.getUserName(),
                author == null ? null : author.getPhoto(),
                authorPremium,
                tweet.getLocation(),
                tweet.getParent() != null ? tweet.getParent().getId() : null,
                toOriginalTweetResponse(tweet, premiumUserIds),
                toStatisticsResponse(tweet.getStatistics(), tweet.getId())
        );
    }

    /**
     * The original tweet of a retweet or a quote. It carries the author of the original tweet,
     * so a client can show whose tweet was retweeted or quoted.
     */
    private OriginalTweetResponse toOriginalTweetResponse(Tweet tweet, Set<Long> premiumUserIds) {
        Tweet original = tweet.getParent();
        if (original == null) {
            return null;
        }
        User originalAuthor = original.getUser();
        return new OriginalTweetResponse(
                original.getId(),
                original.getContent(),
                originalAuthor == null ? null : originalAuthor.getId(),
                originalAuthor == null ? null : originalAuthor.getUserName(),
                originalAuthor == null ? null : originalAuthor.getPhoto(),
                isPremium(originalAuthor, premiumUserIds),
                toStatisticsResponse(original.getStatistics(), original.getId())
        );
    }

    private boolean isPremium(User author, Set<Long> premiumUserIds) {
        if (author == null || author.getId() == null) {
            return false;
        }
        return premiumUserIds.contains(author.getId());
    }

    private Set<Long> premiumUserIds(Collection<Tweet> tweets) {
        if (tweets == null || tweets.isEmpty()) {
            return Set.of();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (Tweet tweet : tweets) {
            if (tweet == null) {
                continue;
            }
            addUserId(userIds, tweet.getUser());
            if (tweet.getParent() != null) {
                addUserId(userIds, tweet.getParent().getUser());
            }
        }
        if (userIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> premiumUserIds = subscriptionService.findPremiumUserIds(List.copyOf(userIds));
        return premiumUserIds == null ? Set.of() : premiumUserIds;
    }

    private void addUserId(Set<Long> userIds, User user) {
        if (user != null && user.getId() != null) {
            userIds.add(user.getId());
        }
    }

    private StatisticsResponse toStatisticsResponse(Statistics statistics, Long tweetId) {
        if (statistics == null) {
            return new StatisticsResponse(tweetId, 0L, 0L, 0L, 0L, 0L, 0L);
        }
        return new StatisticsResponse(
                tweetId,
                value(statistics.getViews()),
                value(statistics.getLikes()),
                value(statistics.getComments()),
                value(statistics.getBookmarks()),
                value(statistics.getRetweets()),
                value(statistics.getQuotes())
        );
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}
