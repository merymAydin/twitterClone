package com.example.twitter_challenge.exception;

/**
 * Stripe is not configured correctly for this environment, or the configured credentials are
 * not accepted by Stripe. The message is safe to show to an authenticated client and must not
 * contain any part of a secret key.
 */
public class StripeConfigurationException extends RuntimeException {

    public StripeConfigurationException(String message) {
        super(message);
    }

    public StripeConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
