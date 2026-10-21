package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.Statistics;
import com.example.twitter_challenge.Entity.Tweet;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.TweetRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository level behaviour of the Explore feed. The queries are executed against the real
 * database so the soft delete filter, the ordering and the pagination are verified instead of
 * being asserted on a mock.
 */
@DataJpaTest
class ExploreFeedRepositoryTest {

    @Autowired
    private TweetRepository tweetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User alice;
    private User bob;
    private User carol;

    @BeforeEach
    void setUp() {
        alice = persistUser("alice", "alice@example.com");
        bob = persistUser("bob", "bob@example.com");
        carol = persistUser("carol", "carol@example.com");
    }

    @Test
    void exploreReturnsTweetsOfUsersThatAreNotFollowed() {
        persistTweet(alice, "alice tweet", 1_000L);
        persistTweet(bob, "bob tweet", 2_000L);

        Page<Tweet> page = tweetRepository.findExplore(PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
        assertEquals(
                Set.of("alice tweet", "bob tweet"),
                contents(page)
        );
    }

    @Test
    void exploreIncludesTheOwnTweetsOfTheCaller() {
        persistTweet(alice, "own tweet", 1_000L);

        Page<Tweet> page = tweetRepository.findExplore(PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("own tweet", page.getContent().get(0).getContent());
    }

    @Test
    void exploreExcludesSoftDeletedTweets() {
        persistTweet(alice, "visible tweet", 1_000L);
        Tweet deleted = persistTweet(alice, "deleted tweet", 2_000L);
        deleted.setIsDeleted(true);
        entityManager.merge(deleted);
        entityManager.flush();
        entityManager.clear();

        Page<Tweet> page = tweetRepository.findExplore(PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals(Set.of("visible tweet"), contents(page));
    }

    @Test
    void exploreExcludesTweetsOfSoftDeletedAuthors() {
        persistTweet(alice, "alive tweet", 1_000L);
        User deletedAuthor = persistUser("ghost", "ghost@example.com");
        persistTweet(deletedAuthor, "ghost tweet", 2_000L);
        deletedAuthor.setIsDeleted(true);
        entityManager.merge(deletedAuthor);
        entityManager.flush();
        entityManager.clear();

        Page<Tweet> page = tweetRepository.findExplore(PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals(Set.of("alive tweet"), contents(page));
    }

    @Test
    void exploreReturnsTheNewestTweetsFirst() {
        persistTweet(alice, "oldest", 1_000L);
        persistTweet(bob, "newest", 3_000L);
        persistTweet(carol, "middle", 2_000L);

        Page<Tweet> page = tweetRepository.findExplore(PageRequest.of(0, 10));

        assertEquals(List.of("newest", "middle", "oldest"), orderedContents(page));
    }

    @Test
    void explorePaginatesWithoutRepeatingOrSkippingATweet() {
        persistTweet(alice, "first", 1_000L);
        persistTweet(bob, "second", 2_000L);
        persistTweet(carol, "third", 3_000L);
        persistTweet(alice, "fourth", 4_000L);

        Page<Tweet> firstPage = tweetRepository.findExplore(PageRequest.of(0, 2));
        Page<Tweet> secondPage = tweetRepository.findExplore(PageRequest.of(1, 2));

        assertEquals(4, firstPage.getTotalElements());
        assertEquals(4, secondPage.getTotalElements());
        assertEquals(List.of("fourth", "third"), orderedContents(firstPage));
        assertEquals(List.of("second", "first"), orderedContents(secondPage));

        Set<String> combined = new HashSet<>(contents(firstPage));
        combined.addAll(contents(secondPage));
        assertEquals(4, combined.size(), "a tweet must not be returned on two pages");
    }

    @Test
    void aTweetWithoutACreationTimestampDoesNotJumpAheadOfTheDatedTweets() {
        persistTweet(alice, "dated tweet", 3_000L);
        Tweet legacy = persistTweet(bob, "legacy tweet", 1_000L);
        // Tweets stored before the creation timestamp existed have a null created_at.
        entityManager.getEntityManager()
                .createNativeQuery("update tweets set created_at = null where id = ?")
                .setParameter(1, legacy.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Page<Tweet> page = tweetRepository.findExplore(PageRequest.of(0, 10));

        assertEquals(List.of("dated tweet", "legacy tweet"), orderedContents(page));
    }

    @Test
    void exploreNeverReturnsTheSameTweetTwice() {
        persistTweet(alice, "tweet", 1_000L);

        Page<Tweet> page = tweetRepository.findExplore(PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Tweet::getId).toList();
        assertEquals(ids.size(), new HashSet<>(ids).size());
    }

    @Test
    void exploreExcludesTheTweetsOfUsersThatAreNotActiveOnly() {
        User softDeleted = persistUser("soft", "soft@example.com");
        persistTweet(softDeleted, "soft tweet", 1_000L);
        softDeleted.setIsDeleted(true);
        entityManager.merge(softDeleted);
        entityManager.flush();
        entityManager.clear();

        Page<Tweet> page = tweetRepository.findExplore(PageRequest.of(0, 10));

        assertTrue(page.getContent().isEmpty());
        assertFalse(page.getContent().stream().anyMatch(tweet -> "soft tweet".equals(tweet.getContent())));
    }

    private Set<String> contents(Page<Tweet> page) {
        return new HashSet<>(orderedContents(page));
    }

    private List<String> orderedContents(Page<Tweet> page) {
        return page.getContent().stream().map(Tweet::getContent).toList();
    }

    private User persistUser(String username, String email) {
        User user = new User();
        user.setUserName(username);
        user.setEmail(email);
        user.setPassword("password");
        user.setIsDeleted(false);
        return userRepository.saveAndFlush(user);
    }

    private Tweet persistTweet(User author, String content, long createdAt) {
        Tweet tweet = new Tweet();
        tweet.setUser(author);
        tweet.setContent(content);
        tweet.setIsDeleted(false);
        tweet.setCreatedAt(new Date(createdAt));

        Statistics statistics = new Statistics();
        statistics.setTweet(tweet);
        statistics.setViews(0L);
        statistics.setLikes(1L);
        statistics.setComments(2L);
        statistics.setBookmarks(3L);
        statistics.setRetweets(4L);
        statistics.setQuotes(5L);
        statistics.setIsDeleted(false);
        entityManager.persist(statistics);

        return tweetRepository.saveAndFlush(tweet);
    }
}
