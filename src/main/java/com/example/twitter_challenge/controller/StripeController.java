package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.interfaces.StripeService;
import com.example.twitter_challenge.Service.interfaces.UserService;
import com.example.twitter_challenge.dto.response.StripeConfigResponse;
import com.example.twitter_challenge.dto.response.SubscriptionResponse;
import com.example.twitter_challenge.dto.response.SubscriptionStatusResponse;
import com.example.twitter_challenge.exception.ForbiddenException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/stripe")
public class StripeController {

    private final StripeService stripeService;
    private final UserService userService;

    public StripeController(StripeService stripeService, UserService userService) {
        this.stripeService = stripeService;
        this.userService = userService;
    }

    /**
     * Returns the publishable key and current Stripe mode for Stripe.js. The secret key is never
     * exposed. A missing key or a test/live mismatch is returned as 503 by the service.
     */
    @GetMapping("/config")
    public StripeConfigResponse getConfig() {
        return stripeService.getStripeConfig();
    }

    @PostMapping("/customer")
    public String createCustomer(Authentication authentication) {
        return stripeService.createCustomer(currentUserId(authentication));
    }

    @PostMapping("/subscription/{userId}")
    public String createSubscription(@PathVariable Long userId, Authentication authentication) {
        return stripeService.createSubscription(requireOwnUser(authentication, userId));
    }

    @PostMapping("/subscription/{userId}/cancel")
    public SubscriptionResponse cancelSubscription(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean immediately,
            Authentication authentication
    ) {
        return stripeService.cancelSubscription(
                requireOwnUser(authentication, userId),
                immediately
        );
    }

    @GetMapping("/status")
    public SubscriptionStatusResponse getStatus(Authentication authentication) {
        return stripeService.getSubscriptionStatus(currentUserId(authentication));
    }

    @GetMapping("/subscription/{userId}")
    public SubscriptionStatusResponse getSubscriptionStatus(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        return stripeService.getSubscriptionStatus(requireOwnUser(authentication, userId));
    }

    @GetMapping("/subscriptions/{userId}")
    public List<SubscriptionResponse> getSubscriptionHistory(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        return stripeService.getSubscriptionHistory(requireOwnUser(authentication, userId));
    }

    @PostMapping("/payment-method/{userId}")
    public void attachPaymentMethod(
            @PathVariable Long userId,
            @RequestParam String paymentMethodId,
            Authentication authentication
    ) {
        stripeService.attachPaymentMethod(requireOwnUser(authentication, userId), paymentMethodId);
    }

    @PostMapping("/setup-intent/{userId}")
    public String createSetupIntent(@PathVariable Long userId, Authentication authentication) {
        return stripeService.createSetupIntent(requireOwnUser(authentication, userId));
    }

    /**
     * Resolves the authenticated user's local id from the verified JWT principal.
     * The JWT filter stores the email as a String principal; getName() also keeps this
     * working if the principal is ever changed to a UserDetails instance.
     */
    private Long currentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ForbiddenException("Authentication is required");
        }

        String email = authentication.getName();
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            throw new ForbiddenException("Authentication is required");
        }

        return userService.findByEmail(email).userId();
    }

    /**
     * Ownership is determined exclusively by the authenticated principal. The path variable is
     * only checked for consistency so that an authenticated user cannot address another user's
     * Stripe resources; it is never used as the source of the acting user's id.
     */
    private Long requireOwnUser(Authentication authentication, Long requestedUserId) {
        Long authenticatedUserId = currentUserId(authentication);
        if (!authenticatedUserId.equals(requestedUserId)) {
            throw new ForbiddenException("You are not allowed to act on another user's Stripe data");
        }
        return authenticatedUserId;
    }
}
