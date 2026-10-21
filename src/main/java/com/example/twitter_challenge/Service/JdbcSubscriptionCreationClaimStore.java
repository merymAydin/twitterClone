package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Service.interfaces.SubscriptionCreationClaimStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL-backed claim store. The primary key on user_id is the concurrency primitive:
 * only one INSERT for a user can succeed, even across application instances.
 */
@Repository
public class JdbcSubscriptionCreationClaimStore implements SubscriptionCreationClaimStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSubscriptionCreationClaimStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> tryAcquire(Long userId, Duration ttl) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");

        Instant now = Instant.now();
        Timestamp nowTimestamp = Timestamp.from(now);
        Timestamp expiresAt = Timestamp.from(now.plus(ttl));

        // Allow a new attempt to take over a claim left behind by a crashed process.
        jdbcTemplate.update(
                "DELETE FROM subscription_creation_claims " +
                        "WHERE user_id = ? AND expires_at < ?",
                userId,
                nowTimestamp
        );

        String claimToken = UUID.randomUUID().toString();

        try {
            jdbcTemplate.update(
                    "INSERT INTO subscription_creation_claims " +
                            "(user_id, claim_token, created_at, expires_at) " +
                            "VALUES (?, ?, ?, ?)",
                    userId,
                    claimToken,
                    nowTimestamp,
                    expiresAt
            );
            return Optional.of(claimToken);
        } catch (DuplicateKeyException e) {
            // Another request owns an unexpired claim for this user.
            return Optional.empty();
        }
    }

    @Override
    public void release(Long userId, String claimToken) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(claimToken, "claimToken must not be null");

        jdbcTemplate.update(
                "DELETE FROM subscription_creation_claims " +
                        "WHERE user_id = ? AND claim_token = ?",
                userId,
                claimToken
        );
    }
}
