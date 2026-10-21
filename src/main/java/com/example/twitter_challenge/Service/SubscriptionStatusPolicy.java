package com.example.twitter_challenge.Service;

import java.util.Set;

/**
 * Central policy for Stripe subscription statuses.
 *
 * <p>Entitlement (premium access) and creation blocking are deliberately different:
 * an unpaid/paused/incomplete subscription is not premium but still represents an unresolved
 * billing relationship and must not be duplicated. Terminal states never block a new attempt.</p>
 */
public final class SubscriptionStatusPolicy {

    private static final Set<String> ENTITLED_STATUSES =
            Set.of("active", "trialing", "past_due");

    private static final Set<String> CREATION_BLOCKING_STATUSES =
            Set.of("active", "trialing", "past_due", "incomplete", "unpaid", "paused");

    private static final Set<String> TERMINAL_STATUSES =
            Set.of("canceled", "incomplete_expired");

    private SubscriptionStatusPolicy() {
    }

    public static boolean isEntitled(String status) {
        return status != null && ENTITLED_STATUSES.contains(status);
    }

    public static boolean blocksCreation(String status) {
        return status != null && CREATION_BLOCKING_STATUSES.contains(status);
    }

    public static boolean isTerminal(String status) {
        return status != null && TERMINAL_STATUSES.contains(status);
    }

    public static Set<String> entitledStatuses() {
        return ENTITLED_STATUSES;
    }

    public static Set<String> creationBlockingStatuses() {
        return CREATION_BLOCKING_STATUSES;
    }
}
