package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.StatisticsRepository;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.NotificationService;
import com.example.twitter_challenge.Service.interfaces.TweetService;
import com.example.twitter_challenge.dto.request.CreateTweetRequest;
import com.example.twitter_challenge.dto.request.UpdateTweetRequest;
import com.example.twitter_challenge.dto.response.StatisticsResponse;
import com.example.twitter_challenge.dto.response.TweetResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import com.example.twitter_challenge.exception.StatisticsNotFoundException;
import com.example.twitter_challenge.exception.TweetNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import com.example.twitter_challenge.Entity.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TweetServiceImpl implements TweetService {
    private static final Pattern MENTION_PATTERN = Pattern.compile("@[a-zA-Z0-9_]+");

    private final UserRepository userRepository;
    private final TweetRepository tweetRepository;
    private final StatisticsRepository statisticsRepository;
    private final NotificationService notificationService;
    private final TweetResponseMapper tweetResponseMapper;
    private final MentionNotifier mentionNotifier;

    public TweetServiceImpl(
            UserRepository userRepository,
            TweetRepository tweetRepository,
            StatisticsRepository statisticsRepository,
            NotificationService notificationService,
            TweetResponseMapper tweetResponseMapper,
            MentionNotifier mentionNotifier
    ) {
        this.userRepository = userRepository;
        this.tweetRepository = tweetRepository;
        this.statisticsRepository = statisticsRepository;
        this.notificationService = notificationService;
        this.tweetResponseMapper = tweetResponseMapper;
        this.mentionNotifier = mentionNotifier;
    }

    private List<String> extractMentionedUsernames(String content) {
        Set<String> usernames = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content == null ? "" : content);
        while (matcher.find()) {
            usernames.add(matcher.group().substring(1));
        }
        return new ArrayList<>(usernames);
    }

    private Statistics createStatistics(Tweet tweet) {
        Statistics statistics = new Statistics();
        statistics.setViews(0L);
        statistics.setRetweets(0L);
        statistics.setBookmarks(0L);
        statistics.setComments(0L);
        statistics.setLikes(0L);
        statistics.setQuotes(0L);
        statistics.setTweet(tweet);
        return statistics;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private Page<TweetResponse> toResponsePage(Page<Tweet> tweets) {
        return tweetResponseMapper.toResponsePage(tweets);
    }

    private TweetResponse toResponse(Tweet tweet) {
        return tweetResponseMapper.toResponse(tweet);
    }

    private User currentUser() {
        String currentUserEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findFirstByEmailAndIsDeletedFalseOrderByIdAsc(currentUserEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private void updateCounter(Long tweetId, Counter counter, long delta) {
        Statistics statistics = statisticsRepository.findByTweetIdForUpdate(tweetId)
                .orElseThrow(() -> new StatisticsNotFoundException(
                        "Statistics for tweet " + tweetId + " not found"
                ));
        switch (counter) {
            case LIKES -> statistics.setLikes(Math.max(0L, value(statistics.getLikes()) + delta));
            case COMMENTS -> statistics.setComments(Math.max(0L, value(statistics.getComments()) + delta));
            case BOOKMARKS -> statistics.setBookmarks(Math.max(0L, value(statistics.getBookmarks()) + delta));
            case RETWEETS -> statistics.setRetweets(Math.max(0L, value(statistics.getRetweets()) + delta));
            case QUOTES -> statistics.setQuotes(Math.max(0L, value(statistics.getQuotes()) + delta));
        }
        statisticsRepository.save(statistics);
    }

    private enum Counter {
        LIKES, COMMENTS, BOOKMARKS, RETWEETS, QUOTES
    }

    @Transactional
    @Override
    public TweetResponse createTweet(CreateTweetRequest request) {
        if (request.parentId() != null) {
            // Quoting is a separate operation: it has its own endpoint, its own counter and its
            // own mention handling. Creating a tweet with a parent here would let the same quote
            // be produced through two paths and would count the quote twice.
            throw new IllegalArgumentException(
                    "A tweet cannot be created with a parentId. "
                            + "Use POST /tweet/{id}/quote to quote a tweet."
            );
        }

        User user = currentUser();

        Tweet tweet = new Tweet();
        tweet.setContent(request.content());
        tweet.setUser(user);
        tweet.setLocation(request.location());
        Tweet savedTweet = tweetRepository.save(tweet);

        Statistics statistics = statisticsRepository.save(createStatistics(savedTweet));
        savedTweet.setStatistics(statistics);

        mentionNotifier.notifyMentionedUsers(
                request.content(),
                user,
                "You were mentioned in a tweet",
                Set.of()
        );

        log.info("Tweet created: tweetId={}, userId={}", savedTweet.getId(), user.getId());
        return toResponse(savedTweet);
    }

    @Transactional
    @Override
    public void removeTweet(Long id, Long userId) {
        Tweet tweet = tweetRepository.findVisibleById(id)
                .orElseThrow(() -> new TweetNotFoundException("Tweet with " + id + " not found"));

        if (!tweet.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You are not allowed to remove this tweet");
        }

        if (tweet.getParent() != null) {
            if (tweet.getContent() == null) {
                updateCounter(tweet.getParent().getId(), Counter.RETWEETS, -1);
            } else {
                updateCounter(tweet.getParent().getId(), Counter.QUOTES, -1);
            }
        }

        // Soft deletion keeps foreign keys and historical counters intact. Child retweets/quotes
        // are hidden as well so the feed cannot expose content whose parent is gone.
        for (Tweet child : tweetRepository.findByParentIdAndIsDeletedFalse(tweet.getId())) {
            child.setIsDeleted(true);
            tweetRepository.save(child);
        }
        tweet.setIsDeleted(true);
        tweetRepository.save(tweet);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<TweetResponse> findAllTweets(Pageable pageable) {
        return toResponsePage(tweetRepository.findVisible(pageable));
    }

    @Transactional(readOnly = true)
    @Override
    public TweetResponse findTweetById(Long id) {
        Tweet tweet = tweetRepository.findVisibleById(id)
                .orElseThrow(() -> new TweetNotFoundException("Tweet with " + id + " not found"));
        return toResponse(tweet);
    }

    @Transactional
    @Override
    public TweetResponse update(Long id, UpdateTweetRequest request, Long currentUserId) {
        Tweet tweet = tweetRepository.findVisibleById(id)
                .orElseThrow(() -> new TweetNotFoundException("Tweet with " + id + " not found"));
        if (!tweet.getUser().getId().equals(currentUserId)) {
            throw new ForbiddenException("You are not allowed to update this tweet");
        }
        if (tweet.getContent() == null) {
            throw new ForbiddenException("Retweets cannot be edited");
        }
        tweet.setContent(request.content());
        tweetRepository.save(tweet);

        mentionNotifier.notifyMentionedUsers(
                request.content(),
                tweet.getUser(),
                "You were mentioned in a tweet",
                Set.of()
        );

        return toResponse(tweet);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<TweetResponse> findByContentContaining(String keyword, Pageable pageable) {
        return toResponsePage(tweetRepository.findVisibleByContentContaining(keyword, pageable));
    }

    @Transactional
    @Override
    public TweetResponse retweet(Long originalTweetId, Long currentUserId) {
        Tweet originalTweet = tweetRepository.findVisibleById(originalTweetId)
                .orElseThrow(() -> new TweetNotFoundException(
                        "Tweet with " + originalTweetId + " not found"
                ));
        User user = userRepository.findByIdAndIsDeletedFalse(currentUserId)
                .orElseThrow(() -> new UserNotFoundException("User with " + currentUserId + " not found"));

        Optional<Tweet> existingRetweet =
                tweetRepository.findByParentIdAndUserIdAndContentIsNull(
                        originalTweetId,
                        currentUserId
                );
        if (existingRetweet.isPresent()) {
            Tweet existing = existingRetweet.get();
            if (!Boolean.TRUE.equals(existing.getIsDeleted())) {
                throw new ForbiddenException("You already retweeted this tweet");
            }
            // The database unique index covers deleted rows too. Reactivate the existing
            // retweet instead of inserting a duplicate.
            existing.setIsDeleted(false);
            Tweet savedRetweet = tweetRepository.save(existing);
            updateCounter(originalTweetId, Counter.RETWEETS, 1);
            return toResponse(savedRetweet);
        }

        Tweet retweet = new Tweet();
        retweet.setParent(originalTweet);
        retweet.setUser(user);
        Tweet savedRetweet = tweetRepository.save(retweet);

        // A retweet is a tweet like any other: it needs its own counter row, otherwise it
        // cannot be liked or bookmarked and the statistics service would not find it.
        Statistics statistics = statisticsRepository.save(createStatistics(savedRetweet));
        savedRetweet.setStatistics(statistics);

        updateCounter(originalTweetId, Counter.RETWEETS, 1);
        return toResponse(savedRetweet);
    }

    @Transactional
    @Override
    public void unretweet(Long originalTweetId, Long currentUserId) {
        Tweet retweet = tweetRepository
                .findByParentIdAndUserIdAndContentIsNullAndIsDeletedFalse(
                        originalTweetId,
                        currentUserId
                )
                .orElseThrow(() -> new TweetNotFoundException(
                        "Retweet for user " + currentUserId + " not found"
                ));

        updateCounter(originalTweetId, Counter.RETWEETS, -1);
        retweet.setIsDeleted(true);
        tweetRepository.save(retweet);
    }

    @Transactional
    @Override
    public TweetResponse quoteTweet(Long originalTweetId, Long currentUserId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Quote content must not be blank");
        }
        Tweet originalTweet = tweetRepository.findVisibleById(originalTweetId)
                .orElseThrow(() -> new TweetNotFoundException(
                        "Tweet with " + originalTweetId + " not found"
                ));
        User user = userRepository.findByIdAndIsDeletedFalse(currentUserId)
                .orElseThrow(() -> new UserNotFoundException("User with " + currentUserId + " not found"));

        Tweet quote = new Tweet();
        quote.setParent(originalTweet);
        quote.setUser(user);
        quote.setContent(content);
        Tweet savedQuote = tweetRepository.save(quote);
        statisticsRepository.save(createStatistics(savedQuote));
        updateCounter(originalTweetId, Counter.QUOTES, 1);

        mentionNotifier.notifyMentionedUsers(
                content,
                user,
                "You were mentioned in a quote",
                Set.of()
        );

        return toResponse(savedQuote);
    }

    @Transactional
    @Override
    public void removeQuote(Long quoteTweetId, Long currentUserId) {
        Tweet quote = tweetRepository.findVisibleById(quoteTweetId)
                .orElseThrow(() -> new TweetNotFoundException(
                        "Tweet with " + quoteTweetId + " not found"
                ));

        if (!quote.getUser().getId().equals(currentUserId)) {
            throw new ForbiddenException("You are not allowed to remove this quote");
        }
        if (quote.getParent() == null || quote.getContent() == null) {
            throw new ForbiddenException("The tweet is not a quote");
        }

        updateCounter(quote.getParent().getId(), Counter.QUOTES, -1);
        quote.setIsDeleted(true);
        tweetRepository.save(quote);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<Tweet> findAllTweetsByUserId(Long userId, Pageable pageable) {
        return tweetRepository.findVisibleByUserId(userId, pageable);
    }

    /**
     * The ids of the original tweets the given user has retweeted.
     *
     * <p>A client needs this to show the retweet state of a tweet after a reload, because the
     * retweet is stored as a separate tweet and is not part of the tweet itself.</p>
     */
    @Transactional(readOnly = true)
    @Override
    public List<Long> findRetweetedTweetIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Long> ids = tweetRepository.findRetweetedOriginalTweetIds(userId);
        return ids == null ? List.of() : ids;
    }
}
