package com.example.twitter_challenge.Service.interfaces;

import java.time.Duration;
import java.util.Optional;

/**
 * Short-lived, database-backed claim used to serialize subscription creation per user.
 *
 * <p>The claim is not a database transaction and is not held while Stripe is called through
 * JPA. It is a separate row with a unique user id and an expiry timestamp.</p>
 */
public interface SubscriptionCreationClaimStore {

    /**
     * Attempts to acquire the claim for a user.
     *
     * @return the claim token when acquired, or empty when another unexpired claim exists
     */
    Optional<String> tryAcquire(Long userId, Duration ttl);

    /**
     * Releases the claim only when the token still belongs to the caller.
     */
    void release(Long userId, String claimToken);
}
