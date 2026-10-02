package com.example.twitter_challenge.Service.interfaces;


public interface StripeService {
    String createCustomer(String email, String username);
    String createSubscription(Long userId);
    void attachPaymentMethod(Long userId, String paymentMethodId);
    String createSetupIntent(Long userId);
}
