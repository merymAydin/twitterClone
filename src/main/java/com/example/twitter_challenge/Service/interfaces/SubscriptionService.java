package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.dto.response.SubscriptionResponse;
import com.example.twitter_challenge.dto.response.SubscriptionStatusResponse;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SubscriptionService {

    SubscriptionResponse saveFromStripe(
            com.stripe.model.Subscription stripeSubscription,
            Long userId,
            String defaultPaymentMethodId
    );

    /**
     * Persists a webhook-driven update. Older events are ignored based on the Stripe event
     * creation timestamp, so out-of-order delivery cannot roll subscription state backwards.
     */
    SubscriptionResponse saveFromStripe(
            com.stripe.model.Subscription stripeSubscription,
            Long userId,
            String defaultPaymentMethodId,
            Date stripeEventCreatedAt
    );

    Optional<SubscriptionResponse> findActiveSubscription(Long userId);

    Optional<SubscriptionResponse> findBlockingSubscription(Long userId);

    Optional<SubscriptionResponse> findLatestSubscription(Long userId);

    SubscriptionStatusResponse findStatus(Long userId);

    List<SubscriptionResponse> findSubscriptionHistory(Long userId);

    boolean hasActiveSubscription(Long userId);

    boolean hasBlockingSubscription(Long userId);

    long nextCreationGeneration(Long userId);

    /**
     * Resolves the entitled (premium) users out of the given ids in a single query.
     *
     * <p>Used to decorate lists with the premium badge without an N+1 query pattern. Users
     * without an entitled subscription are simply absent from the result.</p>
     */
    Set<Long> findPremiumUserIds(Collection<Long> userIds);
}
