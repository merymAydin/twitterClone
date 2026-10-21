package com.example.twitter_challenge.controller;

import com.example.twitter_challenge.Service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    public StripeWebhookController(StripeWebhookService stripeWebhookService) {
        this.stripeWebhookService = stripeWebhookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Stripe webhook received but STRIPE_WEBHOOK_SECRET is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Webhook secret is not configured");
        }
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest().body("Missing Stripe-Signature header");
        }

        Event event;
        try {
            // Never parse or trust an unsigned payload.
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException exception) {
            log.warn("Rejected Stripe webhook with invalid signature");
            return ResponseEntity.badRequest().body("Invalid Stripe signature");
        } catch (RuntimeException exception) {
            log.warn("Rejected malformed Stripe webhook", exception);
            return ResponseEntity.badRequest().body("Malformed Stripe webhook");
        }

        try {
            stripeWebhookService.handle(event);
            return ResponseEntity.ok("received");
        } catch (RuntimeException exception) {
            // Returning non-2xx makes Stripe retry. The transaction rolled back, so the event
            // will be processable again rather than being permanently marked as handled.
            log.error("Stripe webhook processing failed: eventId={}, type={}",
                    event.getId(), event.getType(), exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Webhook processing failed");
        }
    }
}
