package com.example.twitter_challenge;

import com.example.twitter_challenge.Service.SubscriptionStatusPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionStatusPolicyTest {

    @Test
    void entitledStatusesGrantPremium() {
        assertTrue(SubscriptionStatusPolicy.isEntitled("active"));
        assertTrue(SubscriptionStatusPolicy.isEntitled("trialing"));
        assertTrue(SubscriptionStatusPolicy.isEntitled("past_due"));
    }

    @Test
    void incompleteAndUnpaidDoNotGrantPremium() {
        assertFalse(SubscriptionStatusPolicy.isEntitled("incomplete"));
        assertFalse(SubscriptionStatusPolicy.isEntitled("unpaid"));
        assertFalse(SubscriptionStatusPolicy.isEntitled("paused"));
        assertFalse(SubscriptionStatusPolicy.isEntitled(null));
    }

    @Test
    void unresolvedSubscriptionsBlockDuplicateCreation() {
        assertTrue(SubscriptionStatusPolicy.blocksCreation("active"));
        assertTrue(SubscriptionStatusPolicy.blocksCreation("incomplete"));
        assertTrue(SubscriptionStatusPolicy.blocksCreation("unpaid"));
        assertTrue(SubscriptionStatusPolicy.blocksCreation("paused"));
    }

    @Test
    void terminalStatusesDoNotBlockCreation() {
        assertTrue(SubscriptionStatusPolicy.isTerminal("canceled"));
        assertTrue(SubscriptionStatusPolicy.isTerminal("incomplete_expired"));
        assertFalse(SubscriptionStatusPolicy.blocksCreation("canceled"));
        assertFalse(SubscriptionStatusPolicy.blocksCreation("incomplete_expired"));
    }
}
