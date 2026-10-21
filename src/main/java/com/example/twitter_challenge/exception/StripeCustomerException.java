package com.example.twitter_challenge.exception;

/**
 * A safe, client-visible Stripe customer/subscription state conflict.
 *
 * <p>The message must never contain Stripe identifiers, keys, client secrets, or card data.
 * It is mapped to HTTP 409 because the request cannot be completed until the billing state is
 * reconciled or the customer finishes an authentication step.</p>
 */
public class StripeCustomerException extends RuntimeException {

    public StripeCustomerException(String message) {
        super(message);
    }

    public StripeCustomerException(String message, Throwable cause) {
        super(message, cause);
    }
}
