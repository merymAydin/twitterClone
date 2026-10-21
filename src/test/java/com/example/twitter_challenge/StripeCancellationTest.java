package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.StripeServiceImpl;
import com.example.twitter_challenge.Service.interfaces.SubscriptionCreationClaimStore;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.example.twitter_challenge.dto.response.SubscriptionResponse;
import com.example.twitter_challenge.exception.StripeOperationException;
import com.example.twitter_challenge.exception.SubscriptionNotFoundException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.net.ApiResource;
import com.stripe.param.SubscriptionUpdateParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeCancellationTest {

    private static final String CHANGED_SUBSCRIPTION_JSON = """
            {"id":"sub_1","object":"subscription","customer":"cus_1","status":"active",
             "cancel_at_period_end":true,"items":{"object":"list","data":[]}}
            """;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripeClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private SubscriptionCreationClaimStore subscriptionCreationClaimStore;

    @InjectMocks
    private StripeServiceImpl stripeService;

    @Test
    void activeSubscriptionCancellationAtPeriodEndUsesStripeUpdate() throws Exception {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.findActiveSubscription(1L))
                .thenReturn(Optional.of(subscription("active")));

        Subscription changed = ApiResource.GSON.fromJson(
                CHANGED_SUBSCRIPTION_JSON,
                Subscription.class
        );
        when(stripeClient.v1().subscriptions().update(
                eq("sub_1"),
                any(SubscriptionUpdateParams.class)
        )).thenReturn(changed);
        when(subscriptionService.saveFromStripe(changed, 1L, null))
                .thenReturn(subscription("active"));

        SubscriptionResponse response = stripeService.cancelSubscription(1L, false);

        assertEquals("active", response.status());
        ArgumentCaptor<SubscriptionUpdateParams> captor =
                ArgumentCaptor.forClass(SubscriptionUpdateParams.class);
        verify(stripeClient.v1().subscriptions()).update(eq("sub_1"), captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getCancelAtPeriodEnd());
        verify(stripeClient.v1().subscriptions(), never()).cancel(anyString());
    }

    @Test
    void incompleteSubscriptionIsCancelledImmediatelyEvenWhenPeriodEndWasRequested() throws Exception {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.findActiveSubscription(1L)).thenReturn(Optional.empty());
        when(subscriptionService.findBlockingSubscription(1L))
                .thenReturn(Optional.of(subscription("incomplete")));

        Subscription changed = ApiResource.GSON.fromJson(
                CHANGED_SUBSCRIPTION_JSON,
                Subscription.class
        );
        when(stripeClient.v1().subscriptions().cancel("sub_1")).thenReturn(changed);
        when(subscriptionService.saveFromStripe(changed, 1L, null))
                .thenReturn(subscription("canceled"));

        SubscriptionResponse response = stripeService.cancelSubscription(1L, false);

        assertEquals("canceled", response.status());
        verify(stripeClient.v1().subscriptions()).cancel("sub_1");
        verify(stripeClient.v1().subscriptions(), never())
                .update(anyString(), any(SubscriptionUpdateParams.class));
    }

    @Test
    void noBlockingOrActiveSubscriptionReturnsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(subscriptionService.findActiveSubscription(1L)).thenReturn(Optional.empty());
        when(subscriptionService.findBlockingSubscription(1L)).thenReturn(Optional.empty());

        assertThrows(SubscriptionNotFoundException.class,
                () -> stripeService.cancelSubscription(1L, true));
    }

    @Test
    void subscriptionOwnedByAnotherStripeCustomerIsRejected() throws Exception {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.findActiveSubscription(1L))
                .thenReturn(Optional.of(subscription("active")));

        Subscription changed = ApiResource.GSON.fromJson(
                "{\"id\":\"sub_1\",\"object\":\"subscription\",\"customer\":\"cus_other\"," +
                        "\"status\":\"canceled\",\"items\":{\"object\":\"list\",\"data\":[]}}",
                Subscription.class
        );
        when(stripeClient.v1().subscriptions().cancel("sub_1")).thenReturn(changed);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> stripeService.cancelSubscription(1L, true)
        );
        assertTrue(exception.getMessage().contains("does not belong"));
        verify(subscriptionService, never()).saveFromStripe(any(), any(), any());
    }

    @Test
    void stripeCancellationErrorBecomesOperationException() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(subscriptionService.findActiveSubscription(1L))
                .thenReturn(Optional.of(subscription("active")));

        StripeException stripeException = mock(StripeException.class);
        when(stripeClient.v1().subscriptions().cancel("sub_1")).thenThrow(stripeException);

        StripeOperationException exception = assertThrows(
                StripeOperationException.class,
                () -> stripeService.cancelSubscription(1L, true)
        );
        assertEquals("Failed to cancel subscription", exception.getMessage());
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        user.setStripeCustomerId("cus_1");
        user.setIsDeleted(false);
        return user;
    }

    private SubscriptionResponse subscription(String status) {
        return new SubscriptionResponse(
                10L,
                1L,
                "sub_1",
                status,
                "price_premium",
                null,
                null,
                true
        );
    }
}
