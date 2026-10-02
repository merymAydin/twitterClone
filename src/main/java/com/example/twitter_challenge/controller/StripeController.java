package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.StripeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stripe")
public class StripeController {

    private final StripeService stripeService;

    public StripeController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @PostMapping("/customer")
    public String createCustomer(@RequestParam String email,
                                 @RequestParam String username) {
        return stripeService.createCustomer(email, username);
    }

    @PostMapping("/subscription/{userId}")
    public String createSubscription(@PathVariable Long userId) {
        return stripeService.createSubscription(userId);
    }

    @PostMapping("/payment-method/{userId}")
    public void attachPaymentMethod(
            @PathVariable Long userId,
            @RequestParam String paymentMethodId) {

        stripeService.attachPaymentMethod(userId, paymentMethodId);
    }

    @PostMapping("/setup-intent/{userId}")
    public String createSetupIntent(@PathVariable Long userId) {
        return stripeService.createSetupIntent(userId);
    }
}