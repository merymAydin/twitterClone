package com.example.twitter_challenge;

import com.example.twitter_challenge.Service.JdbcSubscriptionCreationClaimStore;
import com.example.twitter_challenge.Service.interfaces.SubscriptionCreationClaimStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest(properties = "spring.flyway.enabled=false")
@Import(JdbcSubscriptionCreationClaimStore.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JdbcSubscriptionCreationClaimStoreTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SubscriptionCreationClaimStore claimStore;

    @BeforeEach
    void createTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS subscription_creation_claims (
                    user_id BIGINT PRIMARY KEY,
                    claim_token VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    expires_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.update("DELETE FROM subscription_creation_claims");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM subscription_creation_claims");
    }

    @Test
    void onlyOneConcurrentAcquireSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Optional<String>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return claimStore.tryAcquire(1L, Duration.ofMinutes(5));
            });
            Future<Optional<String>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return claimStore.tryAcquire(1L, Duration.ofMinutes(5));
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers did not start");
            start.countDown();

            Optional<String> firstResult = first.get(5, TimeUnit.SECONDS);
            Optional<String> secondResult = second.get(5, TimeUnit.SECONDS);

            long successes = (firstResult.isPresent() ? 1 : 0)
                    + (secondResult.isPresent() ? 1 : 0);
            assertEquals(1, successes, "exactly one concurrent claim must win");
            assertEquals(
                    1L,
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM subscription_creation_claims WHERE user_id = 1",
                            Long.class
                    )
            );

            String winningToken = firstResult.orElseGet(secondResult::orElseThrow);
            claimStore.release(1L, winningToken);

            assertTrue(
                    claimStore.tryAcquire(1L, Duration.ofMinutes(5)).isPresent(),
                    "claim must be acquirable again after release"
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void differentUsersAcquireIndependently() {
        assertTrue(claimStore.tryAcquire(1L, Duration.ofMinutes(5)).isPresent());
        assertTrue(claimStore.tryAcquire(2L, Duration.ofMinutes(5)).isPresent());
    }

    @Test
    void expiredClaimCanBeTakenOverAndOldTokenCannotReleaseNewClaim() {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO subscription_creation_claims " +
                        "(user_id, claim_token, created_at, expires_at) VALUES (?, ?, ?, ?)",
                1L,
                "old-token",
                Timestamp.from(now.minusSeconds(20)),
                Timestamp.from(now.minusSeconds(10))
        );

        Optional<String> newToken = claimStore.tryAcquire(1L, Duration.ofMinutes(5));

        assertTrue(newToken.isPresent(), "expired claim must be replaceable");
        assertNotEquals("old-token", newToken.get());

        claimStore.release(1L, "old-token");
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM subscription_creation_claims WHERE user_id = 1",
                        Long.class
                ),
                "an old token must not release the replacement claim"
        );

        claimStore.release(1L, newToken.get());
        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM subscription_creation_claims WHERE user_id = 1",
                        Long.class
                )
        );
    }
}
