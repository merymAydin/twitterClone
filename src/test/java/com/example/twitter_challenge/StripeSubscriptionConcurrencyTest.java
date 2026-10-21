package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.StripeServiceImpl;
import com.example.twitter_challenge.Service.interfaces.SubscriptionCreationClaimStore;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.example.twitter_challenge.exception.ActiveSubscriptionExistsException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.ApiResource;
import com.stripe.net.RequestOptions;
import com.stripe.param.SubscriptionCreateParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeSubscriptionConcurrencyTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripeClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Test
    void activeSubscriptionPreventsClaimAndStripeCall() {
        User user = user(1L, "cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(1L)).thenReturn(true);

        SubscriptionCreationClaimStore claimStore = mock(SubscriptionCreationClaimStore.class);
        StripeServiceImpl service = serviceWith(claimStore);

        assertThrows(ActiveSubscriptionExistsException.class,
                () -> service.createSubscription(1L));

        verifyNoInteractions(claimStore);
        verifyNoInteractions(stripeClient);
    }

    @Test
    void unresolvedSubscriptionBlocksDuplicateAttemptWithoutGrantingPremium() {
        User user = user(1L, "cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(1L)).thenReturn(false);
        when(subscriptionService.hasBlockingSubscription(1L)).thenReturn(true);

        SubscriptionCreationClaimStore claimStore = mock(SubscriptionCreationClaimStore.class);
        StripeServiceImpl service = serviceWith(claimStore);

        assertThrows(ActiveSubscriptionExistsException.class,
                () -> service.createSubscription(1L));

        verifyNoInteractions(claimStore);
        verifyNoInteractions(stripeClient);
    }

    @Test
    void claimConflictReturns409AndDoesNotCallStripe() {
        User user = user(1L, "cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(1L)).thenReturn(false);

        SubscriptionCreationClaimStore claimStore = mock(SubscriptionCreationClaimStore.class);
        when(claimStore.tryAcquire(eq(1L), any(Duration.class))).thenReturn(Optional.empty());
        StripeServiceImpl service = serviceWith(claimStore);

        assertThrows(ActiveSubscriptionExistsException.class,
                () -> service.createSubscription(1L));

        verify(claimStore, never()).release(anyLong(), anyString());
        verifyNoInteractions(stripeClient);
    }

    @Test
    void twoConcurrentAttemptsOnlyOneStripeSubscriptionIsCreated() throws Exception {
        User user = user(1L, "cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.nextCreationGeneration(1L)).thenReturn(1L);

        ConcurrentHashMap<Long, Boolean> active = new ConcurrentHashMap<>();
        when(subscriptionService.hasActiveSubscription(anyLong()))
                .thenAnswer(invocation -> active.getOrDefault(invocation.getArgument(0), false));
        when(subscriptionService.saveFromStripe(any(), eq(1L), anyString()))
                .thenAnswer(invocation -> {
                    active.put(1L, true);
                    return null;
                });

        stubHappyStripePath("cus_1", "sub_1");

        InMemoryClaimStore claimStore = new InMemoryClaimStore();
        StripeServiceImpl service = serviceWith(claimStore);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<String> task = () -> {
                start.await();
                try {
                    return service.createSubscription(1L);
                } catch (ActiveSubscriptionExistsException e) {
                    return "CONFLICT";
                }
            };

            Future<String> first = executor.submit(task);
            Future<String> second = executor.submit(task);
            start.countDown();

            List<String> results = List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );

            assertEquals(1, results.stream().filter("sub_1"::equals).count());
            assertEquals(1, results.stream().filter("CONFLICT"::equals).count());
        } finally {
            executor.shutdownNow();
        }

        verify(stripeClient.v1().subscriptions(), times(1))
                .create(any(SubscriptionCreateParams.class), any(RequestOptions.class));
        verify(subscriptionService, times(1))
                .saveFromStripe(any(), eq(1L), anyString());
    }

    @Test
    void retryAfterDbFailureUsesSameIdempotencyKeyAndDoesNotCreateDuplicate() throws Exception {
        User user = user(1L, "cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(1L)).thenReturn(false);
        when(subscriptionService.nextCreationGeneration(1L)).thenReturn(1L);

        SubscriptionCreationClaimStore claimStore = mock(SubscriptionCreationClaimStore.class);
        when(claimStore.tryAcquire(eq(1L), any(Duration.class)))
                .thenReturn(Optional.of("claim-1"), Optional.of("claim-2"));

        com.stripe.model.Subscription stripeSubscription = subscription("sub_1", "cus_1");
        stubCustomer("cus_1");
        when(stripeClient.v1().subscriptions().create(
                any(SubscriptionCreateParams.class),
                any(RequestOptions.class)
        )).thenReturn(stripeSubscription);

        doThrow(new RuntimeException("database unavailable"))
                .doReturn(null)
                .when(subscriptionService)
                .saveFromStripe(any(), eq(1L), eq("pm_1"));

        StripeServiceImpl service = serviceWith(claimStore);

        IllegalStateException firstFailure = assertThrows(
                IllegalStateException.class,
                () -> service.createSubscription(1L)
        );
        assertTrue(firstFailure.getMessage().contains("Support intervention"));
        assertTrue(!firstFailure.getMessage().contains("sub_1"));
        verify(claimStore).release(1L, "claim-1");

        assertEquals("sub_1", service.createSubscription(1L));
        verify(claimStore).release(1L, "claim-2");

        ArgumentCaptor<RequestOptions> optionsCaptor =
                ArgumentCaptor.forClass(RequestOptions.class);
        verify(stripeClient.v1().subscriptions(), times(2)).create(
                any(SubscriptionCreateParams.class),
                optionsCaptor.capture()
        );

        assertEquals(2, optionsCaptor.getAllValues().size());
        assertEquals(
                "subscription-create-v1-1-1",
                optionsCaptor.getAllValues().get(0).getIdempotencyKey()
        );
        assertEquals(
                "subscription-create-v1-1-1",
                optionsCaptor.getAllValues().get(1).getIdempotencyKey()
        );
    }

    @Test
    void newAttemptAfterPersistedSubscriptionUsesNewIdempotencyKey() throws Exception {
        User user = user(1L, "cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(1L)).thenReturn(false);
        when(subscriptionService.nextCreationGeneration(1L)).thenReturn(2L);

        SubscriptionCreationClaimStore claimStore = mock(SubscriptionCreationClaimStore.class);
        when(claimStore.tryAcquire(eq(1L), any(Duration.class)))
                .thenReturn(Optional.of("claim-3"));

        stubHappyStripePath("cus_1", "sub_2");
        StripeServiceImpl service = serviceWith(claimStore);

        assertEquals("sub_2", service.createSubscription(1L));

        ArgumentCaptor<RequestOptions> optionsCaptor =
                ArgumentCaptor.forClass(RequestOptions.class);
        verify(stripeClient.v1().subscriptions()).create(
                any(SubscriptionCreateParams.class),
                optionsCaptor.capture()
        );
        assertEquals(
                "subscription-create-v1-1-2",
                optionsCaptor.getValue().getIdempotencyKey()
        );
        verify(claimStore).release(1L, "claim-3");
    }

    @Test
    void stripeApiFailureReleasesClaim() throws Exception {
        User user = user(1L, "cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(1L)).thenReturn(false);
        when(subscriptionService.nextCreationGeneration(1L)).thenReturn(1L);

        SubscriptionCreationClaimStore claimStore = mock(SubscriptionCreationClaimStore.class);
        when(claimStore.tryAcquire(eq(1L), any(Duration.class)))
                .thenReturn(Optional.of("claim-1"));

        StripeException stripeException = mock(StripeException.class);
        stubCustomer("cus_1");
        when(stripeClient.v1().subscriptions().create(
                any(SubscriptionCreateParams.class),
                any(RequestOptions.class)
        )).thenThrow(stripeException);

        StripeServiceImpl service = serviceWith(claimStore);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.createSubscription(1L)
        );

        assertEquals("Failed to create subscription", exception.getMessage());
        assertSame(stripeException, exception.getCause());
        verify(claimStore).release(1L, "claim-1");
        verify(subscriptionService, never()).saveFromStripe(any(), anyLong(), anyString());
    }

    @Test
    void differentUsersDoNotBlockEachOther() throws Exception {
        User firstUser = user(1L, "cus_1");
        User secondUser = user(2L, "cus_2");
        when(userRepository.findById(1L)).thenReturn(Optional.of(firstUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(secondUser));
        when(subscriptionService.nextCreationGeneration(anyLong())).thenReturn(1L);

        ConcurrentHashMap<Long, Boolean> active = new ConcurrentHashMap<>();
        when(subscriptionService.hasActiveSubscription(anyLong()))
                .thenAnswer(invocation -> active.getOrDefault(invocation.getArgument(0), false));
        when(subscriptionService.saveFromStripe(any(), anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    active.put(invocation.getArgument(1), true);
                    return null;
                });

        stubCustomer("cus_1");
        stubCustomer("cus_2");
        when(stripeClient.v1().subscriptions().create(
                any(SubscriptionCreateParams.class),
                any(RequestOptions.class)
        )).thenAnswer(invocation -> {
            SubscriptionCreateParams params = invocation.getArgument(0);
            String customerId = params.getCustomer();
            return subscription(customerId.equals("cus_1") ? "sub_1" : "sub_2", customerId);
        });

        InMemoryClaimStore claimStore = new InMemoryClaimStore();
        StripeServiceImpl service = serviceWith(claimStore);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<String> firstTask = () -> {
                start.await();
                return service.createSubscription(1L);
            };
            Callable<String> secondTask = () -> {
                start.await();
                return service.createSubscription(2L);
            };

            Future<String> first = executor.submit(firstTask);
            Future<String> second = executor.submit(secondTask);
            start.countDown();

            assertEquals("sub_1", first.get(5, TimeUnit.SECONDS));
            assertEquals("sub_2", second.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        verify(stripeClient.v1().subscriptions(), times(2))
                .create(any(SubscriptionCreateParams.class), any(RequestOptions.class));
        verify(subscriptionService, times(2)).saveFromStripe(any(), anyLong(), anyString());
    }

    @Test
    void activeSubscriptionAppearingAfterClaimPreventsStripeCall() {
        User user = user(1L, "cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(1L)).thenReturn(false, true);

        SubscriptionCreationClaimStore claimStore = mock(SubscriptionCreationClaimStore.class);
        when(claimStore.tryAcquire(eq(1L), any(Duration.class)))
                .thenReturn(Optional.of("claim-1"));

        StripeServiceImpl service = serviceWith(claimStore);

        assertThrows(ActiveSubscriptionExistsException.class,
                () -> service.createSubscription(1L));

        verify(claimStore).release(1L, "claim-1");
        verifyNoInteractions(stripeClient);
    }

    private StripeServiceImpl serviceWith(SubscriptionCreationClaimStore claimStore) {
        StripeServiceImpl service = new StripeServiceImpl(
                stripeClient,
                userRepository,
                subscriptionService,
                claimStore
        );
        ReflectionTestUtils.setField(service, "premiumPriceId", "price_premium");
        return service;
    }

    private void stubHappyStripePath(String customerId, String subscriptionId) throws Exception {
        stubCustomer(customerId);
        when(stripeClient.v1().subscriptions().create(
                any(SubscriptionCreateParams.class),
                any(RequestOptions.class)
        )).thenReturn(subscription(subscriptionId, customerId));
    }

    private void stubCustomer(String customerId) throws Exception {
        when(stripeClient.v1().customers().retrieve(customerId))
                .thenReturn(customer(customerId));
    }

    private User user(Long id, String stripeCustomerId) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setUserName("user" + id);
        user.setStripeCustomerId(stripeCustomerId);
        return user;
    }

    private Customer customer(String customerId) {
        String userId = customerId.replace("cus_", "");
        return ApiResource.GSON.fromJson(
                "{\"id\":\"" + customerId + "\",\"object\":\"customer\"," +
                        "\"metadata\":{\"userId\":\"" + userId + "\"," +
                        "\"application\":\"twitter_challenge\"}," +
                        "\"invoice_settings\":{\"default_payment_method\":\"pm_1\"}}",
                Customer.class
        );
    }

    private com.stripe.model.Subscription subscription(String subscriptionId, String customerId) {
        String json = """
                {
                  "id": "%s",
                  "object": "subscription",
                  "customer": "%s",
                  "status": "active",
                  "cancel_at_period_end": false,
                  "default_payment_method": "pm_1",
                  "items": {
                    "object": "list",
                    "data": [
                      {
                        "id": "si_1",
                        "object": "subscription_item",
                        "current_period_start": 1700000000,
                        "current_period_end": 1702592000,
                        "price": {"id": "price_premium", "object": "price"}
                      }
                    ]
                  }
                }
                """.formatted(subscriptionId, customerId);
        return ApiResource.GSON.fromJson(json, com.stripe.model.Subscription.class);
    }

    /**
     * Test double for the claim primitive. It uses an atomic putIfAbsent, matching the
     * production table's primary-key guarantee for the purposes of the service concurrency test.
     */
    private static final class InMemoryClaimStore implements SubscriptionCreationClaimStore {

        private final ConcurrentHashMap<Long, Claim> claims = new ConcurrentHashMap<>();

        @Override
        public Optional<String> tryAcquire(Long userId, Duration ttl) {
            Instant now = Instant.now();
            String token = UUID.randomUUID().toString();
            Claim candidate = new Claim(token, now.plus(ttl));

            Claim existing = claims.putIfAbsent(userId, candidate);
            if (existing == null) {
                return Optional.of(token);
            }
            if (!existing.expiresAt().isAfter(now)
                    && claims.replace(userId, existing, candidate)) {
                return Optional.of(token);
            }
            return Optional.empty();
        }

        @Override
        public void release(Long userId, String claimToken) {
            claims.computeIfPresent(
                    userId,
                    (id, claim) -> claim.token().equals(claimToken) ? null : claim
            );
        }

        private record Claim(String token, Instant expiresAt) {
        }
    }
}
