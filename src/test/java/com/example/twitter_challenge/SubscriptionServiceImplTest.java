package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.Subscription;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.SubscriptionRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.SubscriptionServiceImpl;
import com.example.twitter_challenge.dto.response.SubscriptionResponse;
import com.example.twitter_challenge.dto.response.SubscriptionStatusResponse;
import com.stripe.model.Price;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(subscriptionService, "premiumPriceId", "price_premium");
    }

    @Test
    void incompleteSubscriptionIsPersistedButDoesNotGrantPremium() {
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1")).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionResponse response = subscriptionService.saveFromStripe(
                stripeSubscription("sub_1", "incomplete"),
                1L,
                "pm_1"
        );

        assertEquals("incomplete", response.status());
        assertEquals("price_premium", response.priceId());
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void activeSubscriptionGrantsPremiumStatus() {
        Subscription subscription = existingSubscription("active");
        when(subscriptionRepository.findFirstByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.existsByUserIdAndSubscriptionStatusInAndIsDeletedFalse(
                anyLong(), anyCollection())).thenReturn(true, true);

        SubscriptionStatusResponse status = subscriptionService.findStatus(1L);

        assertTrue(status.premium());
        assertTrue(status.creationBlocked());
        assertEquals("active", status.status());
    }

    @Test
    void incompleteStatusBlocksNewCreationWithoutPremium() {
        Subscription subscription = existingSubscription("incomplete");
        when(subscriptionRepository.findFirstByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.existsByUserIdAndSubscriptionStatusInAndIsDeletedFalse(
                anyLong(), anyCollection())).thenReturn(false, true);

        SubscriptionStatusResponse status = subscriptionService.findStatus(1L);

        assertFalse(status.premium());
        assertTrue(status.creationBlocked());
        assertEquals("incomplete", status.status());
    }

    @Test
    void olderWebhookEventCannotOverwriteNewerState() {
        Subscription subscription = existingSubscription("active");
        subscription.setLastStripeEventCreatedAt(new Date(200_000L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1"))
                .thenReturn(Optional.of(subscription));

        SubscriptionResponse response = subscriptionService.saveFromStripe(
                stripeSubscription("sub_1", "canceled"),
                1L,
                null,
                new Date(100_000L)
        );

        assertEquals("active", response.status());
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    void newerWebhookEventUpdatesState() {
        Subscription subscription = existingSubscription("incomplete");
        subscription.setLastStripeEventCreatedAt(new Date(100_000L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1"))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionResponse response = subscriptionService.saveFromStripe(
                stripeSubscription("sub_1", "active"),
                1L,
                null,
                new Date(200_000L)
        );

        assertEquals("active", response.status());
        assertEquals("price_premium", response.priceId());
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void deletedEventWithoutItemsRetainsAValidPrice() {
        Subscription subscription = existingSubscription("active");
        subscription.setPriceId("price_premium");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_1"))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        com.stripe.model.Subscription deleted = new com.stripe.model.Subscription();
        deleted.setId("sub_1");
        deleted.setCustomer("cus_1");
        deleted.setStatus("canceled");
        deleted.setCancelAtPeriodEnd(false);

        SubscriptionResponse response = subscriptionService.saveFromStripe(
                deleted,
                1L,
                null,
                new Date(300_000L)
        );

        assertEquals("canceled", response.status());
        assertEquals("price_premium", response.priceId());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user@example.com");
        user.setUserName("user");
        user.setIsDeleted(false);
        return user;
    }

    private Subscription existingSubscription(String status) {
        Subscription subscription = new Subscription();
        subscription.setId(10L);
        subscription.setUser(user(1L));
        subscription.setStripeSubscriptionId("sub_1");
        subscription.setStripeCustomerId("cus_1");
        subscription.setSubscriptionStatus(status);
        subscription.setPriceId("price_1");
        subscription.setCancelAtPeriodEnd(false);
        subscription.setIsDeleted(false);
        return subscription;
    }

    private com.stripe.model.Subscription stripeSubscription(String id, String status) {
        com.stripe.model.Subscription subscription = new com.stripe.model.Subscription();
        subscription.setId(id);
        subscription.setCustomer("cus_1");
        subscription.setStatus(status);
        subscription.setCancelAtPeriodEnd(false);

        Price price = new Price();
        price.setId("price_premium");
        SubscriptionItem item = new SubscriptionItem();
        item.setPrice(price);
        item.setCurrentPeriodStart(100L);
        item.setCurrentPeriodEnd(200L);
        SubscriptionItemCollection items = new SubscriptionItemCollection();
        items.setData(List.of(item));
        subscription.setItems(items);
        return subscription;
    }
}
