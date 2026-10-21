package com.example.twitter_challenge.dto.response;

/**
 * Public Stripe.js configuration. Only the publishable key and the current mode are exposed;
 * the secret key never leaves the server.
 */
public record StripeConfigResponse(
        String publishableKey,
        String mode
) {
}
