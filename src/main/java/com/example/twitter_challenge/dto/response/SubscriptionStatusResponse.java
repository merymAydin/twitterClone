package com.example.twitter_challenge.dto.response;

import java.util.Date;

/**
 * Current subscription state for the authenticated user.
 *
 * <p>{@code clientSecret} is only populated when Stripe requires an additional payment
 * authentication step (for example 3D Secure on the first invoice). It is a short-lived Stripe
 * client secret, never card data, and must not be logged. {@code hostedInvoiceUrl} is an optional
 * browser fallback when no client secret is available.</p>
 */
public record SubscriptionStatusResponse(
        boolean premium,
        boolean creationBlocked,
        String status,
        Long subscriptionId,
        String stripeSubscriptionId,
        String priceId,
        Date startDate,
        Date endDate,
        boolean cancelAtPeriodEnd,
        boolean requiresAction,
        String clientSecret,
        String hostedInvoiceUrl,
        String paymentMethodId
) {
}
