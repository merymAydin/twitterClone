package com.example.twitter_challenge;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.StripeServiceImpl;
import com.example.twitter_challenge.Service.interfaces.SubscriptionCreationClaimStore;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.example.twitter_challenge.dto.response.StripeConfigResponse;
import com.example.twitter_challenge.dto.response.SubscriptionStatusResponse;
import com.example.twitter_challenge.exception.ActiveSubscriptionExistsException;
import com.example.twitter_challenge.exception.StripeConfigurationException;
import com.example.twitter_challenge.exception.StripeCustomerException;
import com.example.twitter_challenge.exception.StripeOperationException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.net.ApiResource;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionRetrieveParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeServiceIdentityTest {

    private static final String CUSTOMER_JSON = """
            {
              "id": "cus_1",
              "object": "customer",
              "metadata": {"userId": "1", "application": "twitter_challenge"},
              "invoice_settings": {"default_payment_method": "pm_1"}
            }
            """;

    private static final String SUBSCRIPTION_JSON = """
            {
              "id": "sub_1",
              "object": "subscription",
              "customer": "cus_1",
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
    void createCustomerUsesPersistedUserIdentityAndStableIdempotencyKey() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Customer customer = ApiResource.GSON.fromJson(CUSTOMER_JSON, Customer.class);
        when(stripeClient.v1().customers().create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        )).thenReturn(customer);

        String customerId = stripeService.createCustomer(1L);

        assertEquals("cus_1", customerId);
        assertEquals("cus_1", user.getStripeCustomerId());

        ArgumentCaptor<CustomerCreateParams> paramsCaptor =
                ArgumentCaptor.forClass(CustomerCreateParams.class);
        ArgumentCaptor<RequestOptions> optionsCaptor =
                ArgumentCaptor.forClass(RequestOptions.class);
        verify(stripeClient.v1().customers()).create(
                paramsCaptor.capture(),
                optionsCaptor.capture()
        );

        assertEquals("alice@example.com", paramsCaptor.getValue().getEmail());
        assertEquals("alice", paramsCaptor.getValue().getName());
        assertEquals("customer-create-v1-1", optionsCaptor.getValue().getIdempotencyKey());
        verify(userRepository).save(user);
    }

    @Test
    void existingStripeCustomerIsRetrievedAndReused() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_existing");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Customer existing = ApiResource.GSON.fromJson(
                "{\"id\":\"cus_existing\",\"object\":\"customer\"," +
                        "\"metadata\":{\"userId\":\"1\"}}",
                Customer.class
        );
        when(stripeClient.v1().customers().retrieve("cus_existing")).thenReturn(existing);

        String customerId = stripeService.createCustomer(1L);

        assertEquals("cus_existing", customerId);
        verify(stripeClient.v1().customers()).retrieve("cus_existing");
        verify(stripeClient.v1().customers(), never()).create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void legacyCustomerWithoutUniqueLocalOwnerIsRejected() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_existing");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Customer existing = ApiResource.GSON.fromJson(
                "{\"id\":\"cus_existing\",\"object\":\"customer\"," +
                        "\"email\":\"alice@example.com\"}",
                Customer.class
        );
        when(stripeClient.v1().customers().retrieve("cus_existing")).thenReturn(existing);
        when(userRepository.findAllByStripeCustomerId("cus_existing"))
                .thenReturn(List.of(user, user(2L, "bob@example.com", "bob")));

        StripeCustomerException exception = assertThrows(
                StripeCustomerException.class,
                () -> stripeService.createCustomer(1L)
        );

        assertTrue(exception.getMessage().contains("not verifiably linked"));
        verify(stripeClient.v1().customers(), never()).create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        );
        verify(stripeClient.v1().customers(), never()).update(
                any(String.class),
                any(CustomerUpdateParams.class)
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void legacyCustomerIsAdoptedWhenOwnershipCanBeVerified() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_legacy");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Customer existing = ApiResource.GSON.fromJson(
                "{\"id\":\"cus_legacy\",\"object\":\"customer\"," +
                        "\"email\":\"alice@example.com\"}",
                Customer.class
        );
        when(stripeClient.v1().customers().retrieve("cus_legacy")).thenReturn(existing);
        when(userRepository.findAllByStripeCustomerId("cus_legacy"))
                .thenReturn(List.of(user));
        when(stripeClient.v1().customers().update(
                eq("cus_legacy"),
                any(CustomerUpdateParams.class)
        )).thenReturn(existing);

        String customerId = stripeService.createCustomer(1L);

        assertEquals("cus_legacy", customerId);

        ArgumentCaptor<CustomerUpdateParams> paramsCaptor =
                ArgumentCaptor.forClass(CustomerUpdateParams.class);
        verify(stripeClient.v1().customers()).update(
                eq("cus_legacy"),
                paramsCaptor.capture()
        );

        @SuppressWarnings("unchecked")
        Map<String, String> metadata =
                (Map<String, String>) paramsCaptor.getValue().getMetadata();
        assertEquals("1", metadata.get("userId"));
        assertEquals("twitter_challenge", metadata.get("application"));

        verify(stripeClient.v1().customers(), never()).create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void legacyCustomerWithMismatchedEmailIsRejected() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_legacy_email");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Customer existing = ApiResource.GSON.fromJson(
                "{\"id\":\"cus_legacy_email\",\"object\":\"customer\"," +
                        "\"email\":\"old-address@example.com\"}",
                Customer.class
        );
        when(stripeClient.v1().customers().retrieve("cus_legacy_email")).thenReturn(existing);
        when(userRepository.findAllByStripeCustomerId("cus_legacy_email"))
                .thenReturn(List.of(user));

        StripeCustomerException exception = assertThrows(
                StripeCustomerException.class,
                () -> stripeService.createCustomer(1L)
        );

        assertTrue(exception.getMessage().contains("not verifiably linked"));
        verify(stripeClient.v1().customers(), never()).update(
                any(String.class),
                any(CustomerUpdateParams.class)
        );
        verify(stripeClient.v1().customers(), never()).create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void legacyCustomerWithAnotherApplicationMetadataIsRejected() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_other_app");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Customer existing = ApiResource.GSON.fromJson(
                "{\"id\":\"cus_other_app\",\"object\":\"customer\"," +
                        "\"email\":\"alice@example.com\"," +
                        "\"metadata\":{\"application\":\"another_app\"}}",
                Customer.class
        );
        when(stripeClient.v1().customers().retrieve("cus_other_app")).thenReturn(existing);

        StripeCustomerException exception = assertThrows(
                StripeCustomerException.class,
                () -> stripeService.createCustomer(1L)
        );

        assertTrue(exception.getMessage().contains("not verifiably linked"));
        verify(stripeClient.v1().customers(), never()).update(
                any(String.class),
                any(CustomerUpdateParams.class)
        );
        verify(stripeClient.v1().customers(), never()).create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        );
    }

    @Test
    void existingCustomerLinkedToAnotherUserIsRejected() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_existing");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Customer existing = ApiResource.GSON.fromJson(
                "{\"id\":\"cus_existing\",\"object\":\"customer\"," +
                        "\"metadata\":{\"userId\":\"2\"}}",
                Customer.class
        );
        when(stripeClient.v1().customers().retrieve("cus_existing")).thenReturn(existing);

        StripeCustomerException exception = assertThrows(
                StripeCustomerException.class,
                () -> stripeService.createCustomer(1L)
        );

        assertTrue(exception.getMessage().contains("not verifiably linked"));
        verify(stripeClient.v1().customers(), never()).create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void missingExistingStripeCustomerIsNotSilentlyRecreated() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_gone");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        StripeException missing = mock(StripeException.class);
        when(missing.getStatusCode()).thenReturn(404);
        when(stripeClient.v1().customers().retrieve("cus_gone")).thenThrow(missing);

        StripeCustomerException exception = assertThrows(
                StripeCustomerException.class,
                () -> stripeService.createCustomer(1L)
        );

        assertTrue(exception.getMessage().contains("no longer available"));
        assertSame(missing, exception.getCause());
        verify(stripeClient.v1().customers(), never()).create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        );
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void existingCustomerRetrievalApiErrorIsPropagated() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_existing");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        StripeException apiError = mock(StripeException.class);
        when(apiError.getStatusCode()).thenReturn(500);
        when(stripeClient.v1().customers().retrieve("cus_existing")).thenThrow(apiError);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> stripeService.createCustomer(1L)
        );

        assertEquals("Failed to retrieve Stripe customer", exception.getMessage());
        assertSame(apiError, exception.getCause());
        verify(stripeClient.v1().customers(), never()).create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        );
    }

    @Test
    void createCustomerFailsBeforeStripeCallWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> stripeService.createCustomer(99L));

        verifyNoInteractions(stripeClient);
    }

    @Test
    void createCustomerStripeErrorIsPropagated() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        StripeException apiError = mock(StripeException.class);
        when(stripeClient.v1().customers().create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        )).thenThrow(apiError);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> stripeService.createCustomer(1L)
        );

        assertEquals("Failed to create Stripe customer", exception.getMessage());
        assertSame(apiError, exception.getCause());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void concurrentCreateAttemptsUseTheSameIdempotencyKey() throws Exception {
        User firstRead = user(1L, "alice@example.com", "alice");
        User secondRead = user(1L, "alice@example.com", "alice");
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(firstRead), Optional.of(secondRead));

        Customer customer = ApiResource.GSON.fromJson(CUSTOMER_JSON, Customer.class);
        when(stripeClient.v1().customers().create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        )).thenReturn(customer);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> first = executor.submit(() -> {
                start.await();
                return stripeService.createCustomer(1L);
            });
            Future<String> second = executor.submit(() -> {
                start.await();
                return stripeService.createCustomer(1L);
            });

            start.countDown();

            assertEquals("cus_1", first.get(5, TimeUnit.SECONDS));
            assertEquals("cus_1", second.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        ArgumentCaptor<RequestOptions> optionsCaptor =
                ArgumentCaptor.forClass(RequestOptions.class);
        verify(stripeClient.v1().customers(), times(2)).create(
                any(CustomerCreateParams.class),
                optionsCaptor.capture()
        );

        assertEquals(2, optionsCaptor.getAllValues().size());
        for (RequestOptions options : optionsCaptor.getAllValues()) {
            assertEquals("customer-create-v1-1", options.getIdempotencyKey());
        }
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void createSubscriptionPersistsStripeSubscriptionForAuthenticatedUserId() throws Exception {
        ReflectionTestUtils.setField(stripeService, "premiumPriceId", "price_premium");

        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(1L)).thenReturn(false);
        when(subscriptionService.nextCreationGeneration(1L)).thenReturn(1L);
        when(subscriptionCreationClaimStore.tryAcquire(eq(1L), any(Duration.class)))
                .thenReturn(Optional.of("claim-token"));

        Customer customer = ApiResource.GSON.fromJson(CUSTOMER_JSON, Customer.class);
        when(stripeClient.v1().customers().retrieve("cus_1")).thenReturn(customer);

        com.stripe.model.Subscription stripeSubscription =
                ApiResource.GSON.fromJson(SUBSCRIPTION_JSON, com.stripe.model.Subscription.class);
        ArgumentCaptor<SubscriptionCreateParams> paramsCaptor =
                ArgumentCaptor.forClass(SubscriptionCreateParams.class);
        ArgumentCaptor<RequestOptions> optionsCaptor =
                ArgumentCaptor.forClass(RequestOptions.class);
        when(stripeClient.v1().subscriptions().create(
                paramsCaptor.capture(),
                optionsCaptor.capture()
        )).thenReturn(stripeSubscription);

        String subscriptionId = stripeService.createSubscription(1L);

        assertEquals("sub_1", subscriptionId);
        assertEquals(
                "subscription-create-v1-1-1",
                optionsCaptor.getValue().getIdempotencyKey()
        );
        assertEquals(
                SubscriptionCreateParams.PaymentBehavior.ALLOW_INCOMPLETE,
                paramsCaptor.getValue().getPaymentBehavior()
        );
        assertTrue(paramsCaptor.getValue().getExpand().contains("latest_invoice"));
        assertTrue(
                paramsCaptor.getValue().getExpand().contains("latest_invoice.confirmation_secret")
        );
        assertEquals("cus_1", paramsCaptor.getValue().getCustomer());

        ArgumentCaptor<com.stripe.model.Subscription> subscriptionCaptor =
                ArgumentCaptor.forClass(com.stripe.model.Subscription.class);
        verify(subscriptionService).saveFromStripe(subscriptionCaptor.capture(), eq(1L), eq("pm_1"));
        assertSame(stripeSubscription, subscriptionCaptor.getValue());
        verify(subscriptionCreationClaimStore).release(1L, "claim-token");
    }

    @Test
    void activeSubscriptionBlocksStripeCall() {
        User user = user(1L, "alice@example.com", "alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(1L)).thenReturn(true);

        assertThrows(ActiveSubscriptionExistsException.class,
                () -> stripeService.createSubscription(1L));

        verifyNoInteractions(stripeClient);
    }

    @Test
    void createSetupIntentUsesVerifiedCustomerAndReturnsClientSecret() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Customer customer = ApiResource.GSON.fromJson(CUSTOMER_JSON, Customer.class);
        when(stripeClient.v1().customers().retrieve("cus_1")).thenReturn(customer);

        SetupIntent setupIntent = ApiResource.GSON.fromJson(
                "{\"id\":\"seti_1\",\"object\":\"setup_intent\",\"customer\":\"cus_1\"," +
                        "\"client_secret\":\"seti_1_secret\"}",
                SetupIntent.class
        );
        ArgumentCaptor<SetupIntentCreateParams> paramsCaptor =
                ArgumentCaptor.forClass(SetupIntentCreateParams.class);
        when(stripeClient.v1().setupIntents().create(paramsCaptor.capture()))
                .thenReturn(setupIntent);

        String clientSecret = stripeService.createSetupIntent(1L);

        assertEquals("seti_1_secret", clientSecret);
        assertEquals("cus_1", paramsCaptor.getValue().getCustomer());
        assertEquals("1", paramsCaptor.getValue().getMetadata().get("userId"));
        assertEquals(
                "twitter_challenge",
                paramsCaptor.getValue().getMetadata().get("application")
        );
        assertEquals(SetupIntentCreateParams.Usage.OFF_SESSION, paramsCaptor.getValue().getUsage());
    }

    @Test
    void createSetupIntentWithoutClientSecretIsRejected() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Customer customer = ApiResource.GSON.fromJson(CUSTOMER_JSON, Customer.class);
        when(stripeClient.v1().customers().retrieve("cus_1")).thenReturn(customer);

        SetupIntent setupIntent = ApiResource.GSON.fromJson(
                "{\"id\":\"seti_1\",\"object\":\"setup_intent\",\"customer\":\"cus_1\"}",
                SetupIntent.class
        );
        when(stripeClient.v1().setupIntents().create(any(SetupIntentCreateParams.class)))
                .thenReturn(setupIntent);

        assertThrows(StripeOperationException.class, () -> stripeService.createSetupIntent(1L));
    }

    @Test
    void subscriptionWithoutDefaultPaymentMethodIsRejectedBeforeStripeCall() throws Exception {
        ReflectionTestUtils.setField(stripeService, "premiumPriceId", "price_premium");

        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_no_pm");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(1L)).thenReturn(false);
        when(subscriptionService.nextCreationGeneration(1L)).thenReturn(1L);
        when(subscriptionCreationClaimStore.tryAcquire(eq(1L), any(Duration.class)))
                .thenReturn(Optional.of("claim-token"));

        Customer customer = ApiResource.GSON.fromJson(
                "{\"id\":\"cus_no_pm\",\"object\":\"customer\"," +
                        "\"metadata\":{\"userId\":\"1\",\"application\":\"twitter_challenge\"}}",
                Customer.class
        );
        when(stripeClient.v1().customers().retrieve("cus_no_pm")).thenReturn(customer);

        StripeCustomerException exception = assertThrows(
                StripeCustomerException.class,
                () -> stripeService.createSubscription(1L)
        );

        assertTrue(exception.getMessage().contains("default payment method"));
        verify(stripeClient.v1().subscriptions(), never()).create(
                any(SubscriptionCreateParams.class),
                any(RequestOptions.class)
        );
        verify(subscriptionCreationClaimStore).release(1L, "claim-token");
    }

    @Test
    void missingPremiumPriceIsReportedAsAConfigurationError() {
        ReflectionTestUtils.setField(stripeService, "premiumPriceId", " ");

        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.hasActiveSubscription(1L)).thenReturn(false);
        when(subscriptionService.hasBlockingSubscription(1L)).thenReturn(false);

        assertThrows(StripeConfigurationException.class, () -> stripeService.createSubscription(1L));
        verify(subscriptionCreationClaimStore, never()).tryAcquire(eq(1L), any(Duration.class));
    }

    @Test
    void statusReturnsPaymentActionSecretOnlyForIncompleteSubscription() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.findStatus(1L)).thenReturn(
                new SubscriptionStatusResponse(
                        false, true, "incomplete", 5L, "sub_1", "price_premium",
                        null, null, false, false, null, null, null
                )
        );

        Subscription stripeSubscription = ApiResource.GSON.fromJson(
                "{\"id\":\"sub_1\",\"object\":\"subscription\",\"customer\":\"cus_1\"," +
                        "\"status\":\"incomplete\",\"default_payment_method\":\"pm_1\"," +
                        "\"latest_invoice\":{" +
                        "\"id\":\"in_1\",\"object\":\"invoice\"," +
                        "\"confirmation_secret\":{\"client_secret\":\"pi_1_secret\"," +
                        "\"type\":\"payment_intent\"}," +
                        "\"hosted_invoice_url\":\"https://invoice.stripe.test/in_1\"}}",
                Subscription.class
        );
        when(stripeClient.v1().subscriptions().retrieve(
                eq("sub_1"),
                any(SubscriptionRetrieveParams.class)
        )).thenReturn(stripeSubscription);

        // Stripe is really waiting for the customer to authenticate the payment.
        when(stripeClient.v1().paymentIntents().retrieve("pi_1")).thenReturn(
                ApiResource.GSON.fromJson(
                        "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"status\":\"requires_action\"}",
                        PaymentIntent.class
                )
        );

        SubscriptionStatusResponse response = stripeService.getSubscriptionStatus(1L);

        assertTrue(response.requiresAction());
        assertEquals("pi_1_secret", response.clientSecret());
        assertEquals("https://invoice.stripe.test/in_1", response.hostedInvoiceUrl());
        assertEquals("pm_1", response.paymentMethodId());
    }

    @Test
    void statusDoesNotAskForAuthenticationWhenStripeNeverAttemptedThePayment() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.findStatus(1L)).thenReturn(
                new SubscriptionStatusResponse(
                        false, true, "incomplete", 5L, "sub_1", "price_premium",
                        null, null, false, false, null, null, null
                )
        );

        Subscription stripeSubscription = ApiResource.GSON.fromJson(
                "{\"id\":\"sub_1\",\"object\":\"subscription\",\"customer\":\"cus_1\"," +
                        "\"status\":\"incomplete\",\"default_payment_method\":\"pm_1\"," +
                        "\"latest_invoice\":{" +
                        "\"id\":\"in_1\",\"object\":\"invoice\"," +
                        "\"confirmation_secret\":{\"client_secret\":\"pi_1_secret\"," +
                        "\"type\":\"payment_intent\"}," +
                        "\"hosted_invoice_url\":\"https://invoice.stripe.test/in_1\"}}",
                Subscription.class
        );
        when(stripeClient.v1().subscriptions().retrieve(
                eq("sub_1"),
                any(SubscriptionRetrieveParams.class)
        )).thenReturn(stripeSubscription);

        // The PaymentIntent was never confirmed: no bank authentication is pending.
        when(stripeClient.v1().paymentIntents().retrieve("pi_1")).thenReturn(
                ApiResource.GSON.fromJson(
                        "{\"id\":\"pi_1\",\"object\":\"payment_intent\"," +
                                "\"status\":\"requires_confirmation\"}",
                        PaymentIntent.class
                )
        );

        SubscriptionStatusResponse response = stripeService.getSubscriptionStatus(1L);

        assertFalse(response.requiresAction());
        // The confirmation secret is still delivered so the invoice can be paid.
        assertEquals("pi_1_secret", response.clientSecret());
    }

    @Test
    void statusReconcilesAnIncompleteSubscriptionAfterStripeActivatesIt() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        SubscriptionStatusResponse incomplete = new SubscriptionStatusResponse(
                false, true, "incomplete", 5L, "sub_1", "price_premium",
                null, null, false, false, null, null, null
        );
        SubscriptionStatusResponse active = new SubscriptionStatusResponse(
                true, true, "active", 5L, "sub_1", "price_premium",
                null, null, false, false, null, null, null
        );
        when(subscriptionService.findStatus(1L)).thenReturn(incomplete, active);

        Subscription stripeSubscription = ApiResource.GSON.fromJson(
                "{\"id\":\"sub_1\",\"object\":\"subscription\",\"customer\":\"cus_1\"," +
                        "\"status\":\"active\",\"default_payment_method\":\"pm_1\"," +
                        "\"latest_invoice\":null}",
                Subscription.class
        );
        when(stripeClient.v1().subscriptions().retrieve(
                eq("sub_1"),
                any(SubscriptionRetrieveParams.class)
        )).thenReturn(stripeSubscription);

        SubscriptionStatusResponse response = stripeService.getSubscriptionStatus(1L);

        assertTrue(response.premium());
        verify(subscriptionService).saveFromStripe(stripeSubscription, 1L, "pm_1");
    }

    @Test
    void activeStatusDoesNotCallStripe() {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionService.findStatus(1L)).thenReturn(
                new SubscriptionStatusResponse(
                        true, true, "active", 5L, "sub_1", "price_premium",
                        null, null, false, false, null, null, null
                )
        );

        SubscriptionStatusResponse response = stripeService.getSubscriptionStatus(1L);

        assertTrue(response.premium());
        verifyNoInteractions(stripeClient);
    }

    @Test
    void stripeConfigRejectsTestLiveMismatchAndMissingPublishableKey() {
        ReflectionTestUtils.setField(stripeService, "stripeSecretKey", "sk_test_123");
        ReflectionTestUtils.setField(stripeService, "stripePublishableKey", "pk_test_123");

        StripeConfigResponse config = stripeService.getStripeConfig();

        assertEquals("pk_test_123", config.publishableKey());
        assertEquals("test", config.mode());

        ReflectionTestUtils.setField(stripeService, "stripePublishableKey", "pk_live_123");
        assertThrows(StripeConfigurationException.class, () -> stripeService.getStripeConfig());

        ReflectionTestUtils.setField(stripeService, "stripePublishableKey", "");
        assertThrows(StripeConfigurationException.class, () -> stripeService.getStripeConfig());

        ReflectionTestUtils.setField(stripeService, "stripePublishableKey", "pk_test_123");
        ReflectionTestUtils.setField(stripeService, "stripeSecretKey", "pk_test_123");
        assertThrows(StripeConfigurationException.class, () -> stripeService.getStripeConfig());
    }

    @Test
    void exactMetadataDoesNotOverrideAnotherLocalOwner() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_shared");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Customer existing = ApiResource.GSON.fromJson(
                "{\"id\":\"cus_shared\",\"object\":\"customer\"," +
                        "\"metadata\":{\"userId\":\"1\",\"application\":\"twitter_challenge\"}}",
                Customer.class
        );
        when(stripeClient.v1().customers().retrieve("cus_shared")).thenReturn(existing);
        when(userRepository.findAllByStripeCustomerId("cus_shared"))
                .thenReturn(List.of(user, user(2L, "bob@example.com", "bob")));

        StripeCustomerException exception = assertThrows(
                StripeCustomerException.class,
                () -> stripeService.createCustomer(1L)
        );

        assertTrue(exception.getMessage().contains("another account"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void exactMetadataWithForeignApplicationIsRejected() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_foreign");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Customer existing = ApiResource.GSON.fromJson(
                "{\"id\":\"cus_foreign\",\"object\":\"customer\"," +
                        "\"metadata\":{\"userId\":\"1\",\"application\":\"another_app\"}}",
                Customer.class
        );
        when(stripeClient.v1().customers().retrieve("cus_foreign")).thenReturn(existing);

        assertThrows(StripeCustomerException.class, () -> stripeService.createCustomer(1L));
        verify(stripeClient.v1().customers(), never()).create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        );
    }

    @Test
    void rejectedStripeCredentialsAreReportedAsConfigurationError() throws Exception {
        User user = user(1L, "alice@example.com", "alice");
        user.setStripeCustomerId("cus_existing");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        StripeException authError = mock(StripeException.class);
        when(authError.getStatusCode()).thenReturn(401);
        when(stripeClient.v1().customers().retrieve("cus_existing")).thenThrow(authError);

        assertThrows(StripeConfigurationException.class, () -> stripeService.createCustomer(1L));
        verify(stripeClient.v1().customers(), never()).create(
                any(CustomerCreateParams.class),
                any(RequestOptions.class)
        );
    }

    private User user(Long id, String email, String username) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setUserName(username);
        return user;
    }
}
