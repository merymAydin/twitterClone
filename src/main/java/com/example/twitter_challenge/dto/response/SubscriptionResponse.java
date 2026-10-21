package com.example.twitter_challenge.dto.response;

import java.util.Date;

public record SubscriptionResponse(
        Long id,
        Long userId,
        String stripeSubscriptionId,
        String status,
        String priceId,
        Date startDate,
        Date endDate,
        Boolean cancelAtPeriodEnd
) {
}
