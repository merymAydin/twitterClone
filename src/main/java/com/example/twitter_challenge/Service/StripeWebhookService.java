package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Repository.SubscriptionRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.example.twitter_challenge.exception.StripeOperationException;
import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class StripeWebhookService {

    private static final Set<String> SUBSCRIPTION_EVENTS = Set.of(
            "customer.subscription.created",
            "customer.subscription.updated",
            "customer.subscription.deleted",
            "customer.subscription.paused",
            "customer.subscription.resumed"
    );

    private static final Set<String> INVOICE_EVENTS = Set.of(
            "invoice.paid",
            "invoice.payment_failed",
            "invoice.payment_action_required"
    );

    private final JdbcTemplate jdbcTemplate;
    private final SubscriptionService subscriptionService;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final StripeClient stripeClient;

    public StripeWebhookService(
            JdbcTemplate jdbcTemplate,
            SubscriptionService subscriptionService,
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            StripeClient stripeClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.subscriptionService = subscriptionService;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.stripeClient = stripeClient;
    }

    /**
     * The event row is inserted with ON CONFLICT DO NOTHING in the same transaction as the state
     * change. A duplicate delivery therefore does nothing; a failed processing attempt rolls back
     * the row and Stripe can retry safely.
     */
    @Transactional
    public void handle(Event event) {
        if (event == null || event.getId() == null || event.getId().isBlank()) {
            throw new IllegalArgumentException("Stripe event id is required");
        }

        int inserted = jdbcTemplate.update(
                "INSERT INTO stripe_webhook_events " +
                        "(event_id, event_type, event_created_at, processed_at) " +
                        "VALUES (?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING",
                event.getId(),
                event.getType(),
                event.getCreated() == null ? null : Timestamp.from(Instant.ofEpochSecond(event.getCreated())),
                Timestamp.from(Instant.now())
        );
        if (inserted == 0) {
            log.info("Ignoring duplicate Stripe webhook event: eventId={}, type={}",
                    event.getId(), event.getType());
            return;
        }

        if (SUBSCRIPTION_EVENTS.contains(event.getType())) {
            handleSubscriptionEvent(event);
        } else if (INVOICE_EVENTS.contains(event.getType())) {
            handleInvoiceEvent(event);
        } else {
            log.debug("Stripe webhook event acknowledged without state change: eventId={}, type={}",
                    event.getId(), event.getType());
        }
    }

    private void handleSubscriptionEvent(Event event) {
        Subscription subscription = dataObject(event, Subscription.class);
        Long userId = resolveUserId(subscription.getId(), subscription.getCustomer());
        if (userId == null) {
            log.warn("No local user for Stripe subscription event: eventId={}, subscriptionId={}, customerId={}",
                    event.getId(), subscription.getId(), subscription.getCustomer());
            return;
        }
        subscriptionService.saveFromStripe(
                subscription,
                userId,
                null,
                eventDate(event)
        );
    }

    private void handleInvoiceEvent(Event event) {
        Invoice invoice = dataObject(event, Invoice.class);
        String subscriptionId = invoiceSubscriptionId(invoice);
        if (subscriptionId == null) {
            log.debug("Invoice event has no subscription: eventId={}, invoiceId={}",
                    event.getId(), invoice.getId());
            return;
        }

        Long userId = resolveUserId(subscriptionId, invoice.getCustomer());
        if (userId == null) {
            log.warn("No local user for Stripe invoice event: eventId={}, invoiceId={}, subscriptionId={}",
                    event.getId(), invoice.getId(), subscriptionId);
            return;
        }

        try {
            // The invoice does not contain the authoritative subscription status. Retrieve it so
            // an invoice event cannot grant entitlement based on an unverified frontend flow.
            Subscription subscription = stripeClient.v1().subscriptions().retrieve(subscriptionId);
            subscriptionService.saveFromStripe(
                    subscription,
                    userId,
                    null,
                    eventDate(event)
            );
        } catch (StripeException exception) {
            throw new StripeOperationException("Failed to retrieve subscription for invoice event", exception);
        }
    }

    private String invoiceSubscriptionId(Invoice invoice) {
        if (invoice.getParent() == null || invoice.getParent().getSubscriptionDetails() == null) {
            return null;
        }
        String subscriptionId = invoice.getParent().getSubscriptionDetails().getSubscription();
        return subscriptionId == null || subscriptionId.isBlank() ? null : subscriptionId;
    }

    private Long resolveUserId(String stripeSubscriptionId, String stripeCustomerId) {
        if (stripeSubscriptionId != null) {
            Optional<Long> existingOwner = subscriptionRepository
                    .findByStripeSubscriptionId(stripeSubscriptionId)
                    .map(subscription -> subscription.getUser().getId());
            if (existingOwner.isPresent()) {
                return existingOwner.get();
            }
        }
        if (stripeCustomerId != null) {
            return userRepository.findByStripeCustomerId(stripeCustomerId)
                    .map(user -> user.getId())
                    .orElse(null);
        }
        return null;
    }

    private Date eventDate(Event event) {
        return event.getCreated() == null ? null : new Date(event.getCreated() * 1000L);
    }

    private <T extends StripeObject> T dataObject(Event event, Class<T> type) {
        Optional<StripeObject> safeObject = event.getDataObjectDeserializer().getObject();
        StripeObject object;
        if (safeObject.isPresent()) {
            object = safeObject.get();
        } else {
            // Stripe webhook endpoints can be pinned to an API version newer/older than this
            // library. The SDK refuses "safe" deserialization on version mismatch, but the fields
            // used here are stable; fall back to explicitly unsafe parsing and validate the type.
            try {
                object = event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException exception) {
                throw new IllegalStateException(
                        "Stripe event data could not be deserialized for event " + event.getId(),
                        exception
                );
            }
        }
        if (!type.isInstance(object)) {
            throw new IllegalStateException(
                    "Stripe event " + event.getId() + " has unexpected data type "
                            + object.getClass().getSimpleName()
            );
        }
        return type.cast(object);
    }
}
