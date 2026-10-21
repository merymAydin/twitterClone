package com.example.twitter_challenge.Service.interfaces;

import com.example.twitter_challenge.dto.response.StripeConfigResponse;
import com.example.twitter_challenge.dto.response.SubscriptionResponse;
import com.example.twitter_challenge.dto.response.SubscriptionStatusResponse;

import java.util.List;

public interface StripeService {
    StripeConfigResponse getStripeConfig();

    String createCustomer(Long userId);

    String createSubscription(Long userId);

    void attachPaymentMethod(Long userId, String paymentMethodId);

    String createSetupIntent(Long userId);

    SubscriptionStatusResponse getSubscriptionStatus(Long userId);

    List<SubscriptionResponse> getSubscriptionHistory(Long userId);

    SubscriptionResponse cancelSubscription(Long userId, boolean immediately);
}
