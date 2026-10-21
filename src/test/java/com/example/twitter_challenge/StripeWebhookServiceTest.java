package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.SubscriptionRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.StripeWebhookService;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.stripe.StripeClient;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String SUBSCRIPTION_PAYLOAD = """
            {"id":"evt_1","object":"event","api_version":"2026-01-01","created":1700000000,
             "type":"customer.subscription.updated",
             "data":{"object":{"id":"sub_1","object":"subscription","customer":"cus_1",
                     "status":"active","cancel_at_period_end":false,
                     "items":{"object":"list","data":[]}}}}
            """;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StripeClient stripeClient;

    private StripeWebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new StripeWebhookService(
                jdbcTemplate,
                subscriptionService,
                subscriptionRepository,
                userRepository,
                stripeClient
        );
    }

    @Test
    void duplicateDeliveryIsIgnoredAtomically() throws Exception {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(0);

        webhookService.handle(event(SUBSCRIPTION_PAYLOAD, "evt_1"));

        verifyNoInteractions(subscriptionService);
        verifyNoInteractions(subscriptionRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void subscriptionEventIsAppliedWithItsEventTimestamp() throws Exception {
        User user = new User();
        user.setId(1L);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_1"))
                .thenReturn(Optional.of(user));

        webhookService.handle(event(SUBSCRIPTION_PAYLOAD, "evt_1"));

        verify(subscriptionService).saveFromStripe(
                any(com.stripe.model.Subscription.class),
                eq(1L),
                isNull(),
                eq(new Date(1_700_000_000_000L))
        );
    }

    @Test
    void unknownEventIsAcknowledgedWithoutStateChange() throws Exception {
        String payload = """
                {"id":"evt_2","object":"event","api_version":"2026-01-01","created":1700000000,
                 "type":"customer.created",
                 "data":{"object":{"id":"cus_1","object":"customer"}}}
                """;
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);

        webhookService.handle(event(payload, "evt_2"));

        verifyNoInteractions(subscriptionService);
    }

    @Test
    void eventForUnknownCustomerIsAcknowledgedWithoutCreatingState() throws Exception {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_1"))
                .thenReturn(Optional.empty());

        webhookService.handle(event(SUBSCRIPTION_PAYLOAD, "evt_1"));

        verify(subscriptionService, never()).saveFromStripe(any(), any(), any(), any());
    }

    @Test
    void processingFailurePropagatesSoTheTransactionRollsBack() throws Exception {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_1"))
                .thenReturn(Optional.of(user(1L)));
        when(subscriptionService.saveFromStripe(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("database failure"));

        assertThrows(
                IllegalStateException.class,
                () -> webhookService.handle(event(SUBSCRIPTION_PAYLOAD, "evt_1"))
        );
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Event event(String payload, String eventId) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        return Webhook.constructEvent(payload, sign(payload, timestamp), SECRET);
    }

    private String sign(String payload, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(
                (timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)
        );
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
