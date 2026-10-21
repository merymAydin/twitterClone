package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.StripeService;
import com.example.twitter_challenge.Service.interfaces.SubscriptionCreationClaimStore;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.example.twitter_challenge.dto.response.StripeConfigResponse;
import com.example.twitter_challenge.dto.response.SubscriptionResponse;
import com.example.twitter_challenge.dto.response.SubscriptionStatusResponse;
import com.example.twitter_challenge.exception.ActiveSubscriptionExistsException;
import com.example.twitter_challenge.exception.StripeConfigurationException;
import com.example.twitter_challenge.exception.StripeCustomerException;
import com.example.twitter_challenge.exception.StripeOperationException;
import com.example.twitter_challenge.exception.SubscriptionNotFoundException;
import com.example.twitter_challenge.exception.UserNotFoundException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.model.StripeCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerListParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionRetrieveParams;
import com.stripe.param.SubscriptionUpdateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class StripeServiceImpl implements StripeService {

    private static final String CUSTOMER_IDEMPOTENCY_KEY_PREFIX = "customer-create-v1-";
    private static final String SUBSCRIPTION_IDEMPOTENCY_KEY_PREFIX = "subscription-create-v1-";
    private static final String APPLICATION_METADATA = "twitter_challenge";
    private static final Duration SUBSCRIPTION_CLAIM_TTL = Duration.ofMinutes(5);

    /** A PaymentIntent client secret starts with this prefix and carries the PaymentIntent id. */
    private static final String PAYMENT_INTENT_CLIENT_SECRET_PREFIX = "pi_";

    /** Marks the end of the PaymentIntent id inside a PaymentIntent client secret. */
    private static final String PAYMENT_INTENT_SECRET_SUFFIX = "_secret";

    private final StripeClient stripeClient;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final SubscriptionCreationClaimStore subscriptionCreationClaimStore;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.publishable-key:}")
    private String stripePublishableKey;

    @Value("${stripe.premium-price-id}")
    private String premiumPriceId;

    public StripeServiceImpl(
            StripeClient stripeClient,
            UserRepository userRepository,
            SubscriptionService subscriptionService,
            SubscriptionCreationClaimStore subscriptionCreationClaimStore
    ) {
        this.stripeClient = stripeClient;
        this.userRepository = userRepository;
        this.subscriptionService = subscriptionService;
        this.subscriptionCreationClaimStore = subscriptionCreationClaimStore;
    }

    @Override
    public StripeConfigResponse getStripeConfig() {
        String publishableKey = trimmed(stripePublishableKey);
        if (publishableKey == null || !publishableKey.startsWith("pk_")) {
            throw new StripeConfigurationException(
                    "Stripe publishable key is missing or invalid. Set STRIPE_PUBLISHABLE_KEY to a pk_... key."
            );
        }

        String secretKey = trimmed(stripeSecretKey);
        if (secretKey == null
                || !(secretKey.startsWith("sk_") || secretKey.startsWith("rk_"))) {
            throw new StripeConfigurationException(
                    "Stripe secret key is missing or invalid. Use a server-side sk_... or rk_... key, " +
                            "never a publishable key."
            );
        }

        String secretMode = stripeMode(secretKey);
        String publishableMode = stripeMode(publishableKey);
        if (!"unknown".equals(secretMode)
                && !"unknown".equals(publishableMode)
                && !secretMode.equals(publishableMode)) {
            throw new StripeConfigurationException(
                    "Stripe publishable and secret keys belong to different modes. " +
                            "Use matching test or live credentials."
            );
        }

        return new StripeConfigResponse(publishableKey, secretMode);
    }

    @Override
    public String createCustomer(Long userId) {
        return ensureCustomer(requireActiveUser(userId));
    }

    /**
     * Returns a verified customer id for the user, creating one only when no safe existing
     * customer can be recovered. This is shared by customer, SetupIntent, payment-method, and
     * subscription flows so none of them can operate on an unverified stored id.
     */
    private String ensureCustomer(User user) {
        Long userId = user.getId();
        String existingCustomerId = trimmed(user.getStripeCustomerId());
        if (existingCustomerId != null) {
            return reuseExistingCustomer(userId, existingCustomerId, user);
        }

        // A previous create may have succeeded while the local save was lost, and Stripe only
        // retains idempotency keys for 24 hours. Recover an exact ownership-metadata match before
        // creating another customer.
        String recoveredCustomerId = findCustomerByOwnershipMetadata(user);
        if (recoveredCustomerId != null) {
            ensureNoOtherLocalOwner(recoveredCustomerId, userId);
            user.setStripeCustomerId(recoveredCustomerId);
            userRepository.save(user);
            log.info(
                    "Recovered existing Stripe customer after a lost local write: customerId={}, userId={}",
                    recoveredCustomerId,
                    userId
            );
            return recoveredCustomerId;
        }

        return createNewCustomer(userId, user);
    }

    private String reuseExistingCustomer(Long userId, String customerId, User user) {
        try {
            Customer customer = retrieveVerifiedCustomer(userId, customerId, user);
            log.info(
                    "Reusing verified Stripe customer: customerId={}, userId={}",
                    customer.getId(),
                    userId
            );
            return customer.getId();
        } catch (StripeException e) {
            throw translateCustomerRetrievalException(e, customerId, userId);
        }
    }

    /**
     * Retrieves a stored customer and verifies that it belongs to this application account.
     *
     * <p>Exact {@code metadata.userId} establishes ownership. Customers created before ownership
     * metadata existed may only be adopted when the local mapping is unique to this user and the
     * Stripe email still matches. Any foreign application metadata, another user id, an ambiguous
     * local owner, or a deleted customer is rejected without writing anything.</p>
     */
    private Customer retrieveVerifiedCustomer(Long userId, String customerId, User user)
            throws StripeException {
        Customer customer = stripeClient.v1().customers().retrieve(customerId);
        if (customer == null || trimmed(customer.getId()) == null) {
            throw new StripeCustomerException(
                    "The Stripe customer stored for this account is no longer available. " +
                            "Verify the Stripe mode/account and reconcile the customer mapping."
            );
        }
        if (Boolean.TRUE.equals(customer.getDeleted())) {
            throw new StripeCustomerException(
                    "The Stripe customer stored for this account has been deleted. " +
                            "No replacement was created; billing state needs reconciliation."
            );
        }

        Map<String, String> metadata = customer.getMetadata();
        String application = metadata == null ? null : trimmed(metadata.get("application"));
        if (application != null && !APPLICATION_METADATA.equals(application)) {
            throw ownershipConflict();
        }

        String linkedUserId = metadata == null ? null : trimmed(metadata.get("userId"));
        if (linkedUserId != null) {
            if (!String.valueOf(userId).equals(linkedUserId)) {
                throw ownershipConflict();
            }
            ensureNoOtherLocalOwner(customerId, userId);
            return customer;
        }

        // Customers created before ownership metadata was introduced are safe to adopt only
        // when the local mapping is unique and the Stripe customer still carries the same email.
        if (canAdoptLegacyCustomer(customer, user, userId)) {
            backfillCustomerOwnership(customerId, userId);
            log.info(
                    "Adopted legacy Stripe customer and backfilled ownership metadata: " +
                            "customerId={}, userId={}",
                    customerId,
                    userId
            );
            return customer;
        }

        throw ownershipConflict();
    }

    private StripeCustomerException ownershipConflict() {
        return new StripeCustomerException(
                "The Stripe customer stored for this account is not verifiably linked to it. " +
                        "No billing data was changed; contact support to reconcile the customer mapping."
        );
    }

    private boolean canAdoptLegacyCustomer(Customer customer, User user, Long userId) {
        if (customer.getId() == null || customer.getId().isBlank()
                || Boolean.TRUE.equals(customer.getDeleted())) {
            return false;
        }

        Map<String, String> metadata = customer.getMetadata();
        String linkedUserId = metadata == null ? null : trimmed(metadata.get("userId"));
        if (linkedUserId != null && !linkedUserId.isBlank()) {
            return false;
        }

        String application = metadata == null ? null : trimmed(metadata.get("application"));
        if (application != null && !APPLICATION_METADATA.equals(application)) {
            return false;
        }

        List<User> owners = userRepository.findAllByStripeCustomerId(customer.getId());
        if (owners.size() != 1 || !userId.equals(owners.get(0).getId())) {
            return false;
        }

        return customer.getEmail() != null
                && user.getEmail() != null
                && customer.getEmail().equalsIgnoreCase(user.getEmail());
    }

    private void ensureNoOtherLocalOwner(String customerId, Long userId) {
        List<User> owners = userRepository.findAllByStripeCustomerId(customerId);
        boolean anotherOwner = owners.stream()
                .anyMatch(owner -> owner.getId() != null && !owner.getId().equals(userId));
        if (anotherOwner) {
            // Never move a customer between local accounts; this is the exact ambiguity that must
            // be reconciled by a human, not overwritten by a retry.
            throw new StripeCustomerException(
                    "The Stripe customer is already linked to another account. " +
                            "No billing data was changed; contact support to reconcile it."
            );
        }
    }

    private String findCustomerByOwnershipMetadata(User user) {
        String email = trimmed(user.getEmail());
        if (email == null || user.getId() == null) {
            return null;
        }

        try {
            StripeCollection<Customer> customers = stripeClient.v1()
                    .customers()
                    .list(CustomerListParams.builder()
                            .setEmail(email)
                            .setLimit(100L)
                            .build());

            if (customers == null || customers.getData() == null) {
                return null;
            }

            List<Customer> matches = customers.getData().stream()
                    .filter(customer -> !Boolean.TRUE.equals(customer.getDeleted()))
                    .filter(customer -> String.valueOf(user.getId()).equals(
                            metadataValue(customer, "userId")
                    ))
                    .filter(customer -> {
                        String application = metadataValue(customer, "application");
                        return application == null || APPLICATION_METADATA.equals(application);
                    })
                    .toList();

            if (matches.size() > 1) {
                log.error(
                        "Multiple Stripe customers advertise the same local user id: userId={}",
                        user.getId()
                );
                throw new StripeCustomerException(
                        "Multiple Stripe customers are linked to this account. " +
                                "No new customer was created; billing state needs reconciliation."
                );
            }

            return matches.isEmpty() ? null : trimmed(matches.get(0).getId());
        } catch (StripeException e) {
            // This is a best-effort recovery path. A list failure must not block creation; the
            // stable idempotency key remains the primary duplicate guard.
            log.warn(
                    "Could not check for an existing Stripe customer before creation: userId={}",
                    user.getId(),
                    e
            );
            return null;
        }
    }

    private String metadataValue(Customer customer, String key) {
        Map<String, String> metadata = customer.getMetadata();
        return metadata == null ? null : trimmed(metadata.get(key));
    }

    private void backfillCustomerOwnership(String customerId, Long userId) throws StripeException {
        CustomerUpdateParams params = CustomerUpdateParams.builder()
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("application", APPLICATION_METADATA)
                .build();
        stripeClient.v1().customers().update(customerId, params);
    }

    private String createNewCustomer(Long userId, User user) {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(user.getUserName())
                .putMetadata("userId", String.valueOf(userId))
                .putMetadata("application", APPLICATION_METADATA)
                .build();

        // Stable per-user key: concurrent attempts and retries after a lost response target the same
        // Stripe customer instead of creating a second one. Stripe keeps idempotency keys for 24 hours.
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(CUSTOMER_IDEMPOTENCY_KEY_PREFIX + userId)
                .build();

        try {
            Customer response = stripeClient.v1()
                    .customers()
                    .create(params, requestOptions);

            if (response == null || trimmed(response.getId()) == null) {
                throw new StripeOperationException(
                        "Stripe returned an empty customer response",
                        new IllegalStateException("Missing Stripe customer id")
                );
            }

            // A concurrent request or a stale local row must never let two users share a customer.
            ensureNoOtherLocalOwner(response.getId(), userId);
            user.setStripeCustomerId(response.getId());
            userRepository.save(user);
            log.info("Stripe customer created: customerId={}, userId={}", response.getId(), userId);
            return user.getStripeCustomerId();

        } catch (StripeException e) {
            if (isAuthenticationFailure(e)) {
                log.error("Stripe rejected the configured credentials while creating a customer: userId={}",
                        userId);
                throw new StripeConfigurationException(
                        "Stripe credentials are invalid or do not grant access to the configured account.",
                        e
                );
            }
            log.error("Failed to create Stripe customer: userId={}", userId, e);
            throw new StripeOperationException("Failed to create Stripe customer", e);
        }
    }

    private RuntimeException translateCustomerRetrievalException(
            StripeException exception,
            String customerId,
            Long userId
    ) {
        if (isAuthenticationFailure(exception)) {
            log.error(
                    "Stripe rejected the configured credentials while reading a customer: userId={}",
                    userId
            );
            return new StripeConfigurationException(
                    "Stripe credentials are invalid or do not grant access to the configured account.",
                    exception
            );
        }

        if (isMissingStripeResource(exception)) {
            // A 404 can mean a test/live key mismatch, a different Stripe account, or a deleted
            // customer. The exact cause cannot be known from this response, so never create a
            // replacement or move another customer onto the account automatically.
            log.error(
                    "Stripe customer is unavailable in the current Stripe environment: " +
                            "customerId={}, userId={}, mode={}",
                    customerId,
                    userId,
                    stripeMode(stripeSecretKey),
                    exception
            );
            return new StripeCustomerException(
                    "The Stripe customer stored for this account is no longer available in the " +
                            "current Stripe environment. Verify the Stripe mode/account and " +
                            "reconcile the customer mapping before retrying.",
                    exception
            );
        }

        log.error("Failed to retrieve Stripe customer: customerId={}, userId={}", customerId, userId, exception);
        return new StripeOperationException("Failed to retrieve Stripe customer", exception);
    }

    private boolean isMissingStripeResource(StripeException e) {
        return (e.getStatusCode() != null && e.getStatusCode() == 404)
                || "resource_missing".equals(e.getCode());
    }

    private boolean isAuthenticationFailure(StripeException e) {
        return (e.getStatusCode() != null && (e.getStatusCode() == 401 || e.getStatusCode() == 403))
                || e instanceof com.stripe.exception.AuthenticationException;
    }

    private String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String stripeMode(String key) {
        String value = trimmed(key);
        if (value == null) {
            return "unknown";
        }
        if (value.startsWith("sk_live_") || value.startsWith("rk_live_")
                || value.startsWith("pk_live_")) {
            return "live";
        }
        if (value.startsWith("sk_test_") || value.startsWith("rk_test_")
                || value.startsWith("pk_test_")) {
            return "test";
        }
        return "unknown";
    }

    @Override
    public String createSubscription(Long userId) {
        User user = requireActiveUser(userId);

        // Fast path: do not create a database claim when an entitled or unresolved subscription
        // is already known. Incomplete/unpaid/paused subscriptions block duplicates but do not
        // grant premium access.
        if (subscriptionService.hasActiveSubscription(userId)) {
            throw new ActiveSubscriptionExistsException(
                    "This account already has an active subscription."
            );
        }
        if (subscriptionService.hasBlockingSubscription(userId)) {
            throw new ActiveSubscriptionExistsException(
                    "This account already has a pending subscription. Complete its payment or cancel it before starting another."
            );
        }

        String priceId = trimmed(premiumPriceId);
        if (priceId == null || !priceId.startsWith("price_")) {
            throw new StripeConfigurationException(
                    "The Premium price is missing or invalid. Set STRIPE_PREMIUM_PRICE_ID to a " +
                            "Stripe price_... id before subscribing."
            );
        }

        String customerId = trimmed(user.getStripeCustomerId());
        if (customerId == null) {
            throw new StripeCustomerException(
                    "No Stripe customer is linked to this account yet. Create the customer and " +
                            "confirm a payment method before subscribing."
            );
        }

        String claimToken = subscriptionCreationClaimStore
                .tryAcquire(userId, SUBSCRIPTION_CLAIM_TTL)
                .orElseThrow(() -> new ActiveSubscriptionExistsException(
                        "A subscription is already being created for this account. " +
                                "Wait a moment and refresh the status."
                ));

        try {
            // Re-check after acquiring the claim: a request that completed in between must not
            // be followed by a second Stripe subscription.
            if (subscriptionService.hasActiveSubscription(userId)) {
                throw new ActiveSubscriptionExistsException(
                        "This account already has an active subscription."
                );
            }
            if (subscriptionService.hasBlockingSubscription(userId)) {
                throw new ActiveSubscriptionExistsException(
                        "This account already has a pending subscription. Complete its payment or cancel it before starting another."
                );
            }

            // A retry of an unresolved attempt keeps the same generation because no subscription
            // row was persisted yet. A genuinely new attempt after a persisted subscription gets
            // a new generation and therefore a different Stripe idempotency key.
            long generation = subscriptionService.nextCreationGeneration(userId);
            String idempotencyKey =
                    SUBSCRIPTION_IDEMPOTENCY_KEY_PREFIX + userId + "-" + generation;

            try {
                // Verify ownership again here, not just in createCustomer. A stale DB row must not
                // let this user create a subscription on a customer that belongs to someone else
                // or was created under a different Stripe mode/account.
                Customer customer = retrieveVerifiedCustomer(userId, customerId, user);

                String defaultPaymentMethod = customer.getInvoiceSettings() == null
                        ? null
                        : trimmed(customer.getInvoiceSettings().getDefaultPaymentMethod());

                if (defaultPaymentMethod == null) {
                    throw new StripeCustomerException(
                            "No default payment method is set for this account. Confirm the card " +
                                    "setup and attach the resulting payment method before subscribing."
                    );
                }

                SubscriptionCreateParams.Item item =
                        SubscriptionCreateParams.Item.builder()
                                .setPrice(priceId)
                                .build();

                SubscriptionCreateParams params =
                        SubscriptionCreateParams.builder()
                                .setCustomer(customerId)
                                .addItem(item)
                                .setDefaultPaymentMethod(defaultPaymentMethod)
                                // Stripe must attempt the first invoice itself.
                                //
                                // DEFAULT_INCOMPLETE creates that invoice with auto_advance=false
                                // and never attempts the payment: the PaymentIntent stays at
                                // requires_confirmation and the subscription stays "incomplete"
                                // even for a card that needs no 3D Secure at all (the 4242 test
                                // card included). Nobody can then finish the payment because the
                                // customer never sees a confirmation secret.
                                //
                                // ALLOW_INCOMPLETE lets Stripe attempt the payment: a card
                                // without SCA is charged and the subscription becomes "active",
                                // while a card that needs SCA leaves the PaymentIntent at
                                // requires_action and the subscription at "incomplete" so the
                                // customer can complete the authentication step.
                                .setPaymentBehavior(
                                        SubscriptionCreateParams.PaymentBehavior.ALLOW_INCOMPLETE
                                )
                                // The confirmation secret is a secret field: Stripe only returns it
                                // when it is expanded explicitly. Without this expansion the
                                // client can never finish an SCA challenge.
                                .addExpand("latest_invoice")
                                .addExpand("latest_invoice.confirmation_secret")
                                .putMetadata("userId", String.valueOf(userId))
                                .putMetadata("application", APPLICATION_METADATA)
                                .build();

                Subscription subscription = stripeClient.v1()
                        .subscriptions()
                        .create(
                                params,
                                RequestOptions.builder()
                                        .setIdempotencyKey(idempotencyKey)
                                        .build()
                        );

                if (subscription == null || trimmed(subscription.getId()) == null) {
                    throw new StripeOperationException(
                            "Stripe returned an empty subscription response",
                            new IllegalStateException("Missing Stripe subscription id")
                    );
                }

                log.info(
                        "Stripe subscription created: subscriptionId={}, customerId={}, userId={}, " +
                                "generation={}, status={}, invoiceStatus={}, confirmationSecretPresent={}",
                        subscription.getId(),
                        customerId,
                        userId,
                        generation,
                        subscription.getStatus(),
                        invoiceStatus(subscription),
                        invoiceConfirmationSecret(subscription) != null
                );

                try {
                    subscriptionService.saveFromStripe(subscription, userId, defaultPaymentMethod);
                } catch (RuntimeException persistenceException) {
                    // The Stripe subscription already exists at this point. The claim is released
                    // below; a retry derives the same generation and idempotency key because no
                    // subscription row was persisted, so Stripe returns the same subscription.
                    log.error(
                            "Stripe subscription created but could not be persisted locally: " +
                                    "stripeSubscriptionId={}, customerId={}, userId={}",
                            subscription.getId(),
                            customerId,
                            userId,
                            persistenceException
                    );
                    throw new IllegalStateException(
                            "The subscription was created but could not be saved locally. " +
                                    "Support intervention is required.",
                            persistenceException
                    );
                }

                return subscription.getId();

            } catch (StripeException e) {
                if (isAuthenticationFailure(e)) {
                    log.error(
                            "Stripe rejected the configured credentials while creating a subscription: userId={}",
                            userId
                    );
                    throw new StripeConfigurationException(
                            "Stripe credentials are invalid or do not grant access to the configured account.",
                            e
                    );
                }
                log.error(
                        "Failed to create Stripe subscription: customerId={}, userId={}",
                        customerId,
                        userId,
                        e
                );
                throw new StripeOperationException("Failed to create subscription", e);
            }
        } finally {
            try {
                subscriptionCreationClaimStore.release(userId, claimToken);
            } catch (RuntimeException releaseException) {
                // The claim expires on its own; failing to release it must not hide the original result.
                log.error(
                        "Failed to release subscription creation claim: userId={}",
                        userId,
                        releaseException
                );
            }
        }
    }

    @Override
    public void attachPaymentMethod(Long userId, String paymentMethodId) {
        User user = requireActiveUser(userId);
        String normalizedPaymentMethodId = trimmed(paymentMethodId);

        if (normalizedPaymentMethodId == null) {
            throw new IllegalArgumentException("paymentMethodId must not be blank");
        }

        // The customer is verified before a payment method can be attached or made default.
        String customerId = ensureCustomer(user);

        try {
            PaymentMethod paymentMethod = stripeClient.v1()
                    .paymentMethods()
                    .retrieve(normalizedPaymentMethodId);
            if (paymentMethod == null) {
                throw new StripeCustomerException(
                        "The payment method is no longer available. Confirm the card setup again."
                );
            }

            String attachedCustomerId = trimmed(paymentMethod.getCustomer());
            if (attachedCustomerId == null) {
                PaymentMethodAttachParams attachParams =
                        PaymentMethodAttachParams.builder()
                                .setCustomer(customerId)
                                .build();
                stripeClient.v1()
                        .paymentMethods()
                        .attach(normalizedPaymentMethodId, attachParams);
            } else if (!customerId.equals(attachedCustomerId)) {
                throw new StripeCustomerException(
                        "The payment method belongs to another customer and cannot be used here."
                );
            }

            CustomerUpdateParams updateParams =
                    CustomerUpdateParams.builder()
                            .setInvoiceSettings(
                                    CustomerUpdateParams.InvoiceSettings.builder()
                                            .setDefaultPaymentMethod(normalizedPaymentMethodId)
                                            .build()
                            )
                            .build();
            stripeClient.v1()
                    .customers()
                    .update(customerId, updateParams);
            log.info("Stripe payment method attached and set as default: customerId={}", customerId);

        } catch (StripeException e) {
            if (isAuthenticationFailure(e)) {
                throw new StripeConfigurationException(
                        "Stripe credentials are invalid or do not grant access to the configured account.",
                        e
                );
            }
            log.error("Failed to attach Stripe payment method: customerId={}", customerId, e);
            throw new StripeOperationException("Failed to attach payment method", e);
        }
    }

    @Override
    public String createSetupIntent(Long userId) {
        User user = requireActiveUser(userId);
        // Creating the customer here is safe because ensureCustomer is idempotent and verifies
        // any existing mapping. The frontend therefore cannot accidentally use a stale customer.
        String customerId = ensureCustomer(user);
        try {
            SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                    .setCustomer(customerId)
                    .addPaymentMethodType("card")
                    .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                    .putMetadata("userId", String.valueOf(userId))
                    .putMetadata("application", APPLICATION_METADATA)
                    .build();

            SetupIntent setupIntent = stripeClient.v1()
                    .setupIntents()
                    .create(params);

            if (setupIntent == null || trimmed(setupIntent.getClientSecret()) == null) {
                throw new StripeOperationException(
                        "Stripe returned a SetupIntent without a client secret",
                        new IllegalStateException("Missing SetupIntent client secret")
                );
            }

            log.info(
                    "Stripe SetupIntent created: setupIntentId={}, customerId={}, userId={}",
                    setupIntent.getId(),
                    customerId,
                    userId
            );
            // The client secret is returned directly to the authenticated browser and is never logged.
            return setupIntent.getClientSecret();

        } catch (StripeException e) {
            if (isAuthenticationFailure(e)) {
                throw new StripeConfigurationException(
                        "Stripe credentials are invalid or do not grant access to the configured account.",
                        e
                );
            }
            log.error("Failed to create SetupIntent: customerId={}, userId={}", customerId, userId, e);
            throw new StripeOperationException("Failed to create SetupIntent", e);
        }
    }

    @Override
    public SubscriptionStatusResponse getSubscriptionStatus(Long userId) {
        User user = requireActiveUser(userId);
        SubscriptionStatusResponse localStatus = subscriptionService.findStatus(userId);

        // Only an incomplete subscription needs a browser-side confirmation step. Keep the status
        // endpoint cheap for active/canceled subscriptions and never expose a secret for them.
        if (!"incomplete".equals(localStatus.status())
                || trimmed(localStatus.stripeSubscriptionId()) == null) {
            return localStatus;
        }

        try {
            Subscription stripeSubscription = stripeClient.v1()
                    .subscriptions()
                    .retrieve(
                            localStatus.stripeSubscriptionId(),
                            SubscriptionRetrieveParams.builder()
                                    .addExpand("latest_invoice")
                                    // The confirmation secret is a secret field and is only
                                    // returned when it is expanded explicitly.
                                    .addExpand("latest_invoice.confirmation_secret")
                                    .build()
                    );

            String customerId = trimmed(user.getStripeCustomerId());
            if (stripeSubscription == null
                    || customerId == null
                    || stripeSubscription.getCustomer() == null
                    || !customerId.equals(stripeSubscription.getCustomer())) {
                log.error(
                        "Refusing to expose a payment action for a subscription that is not linked " +
                                "to the user's verified Stripe customer: subscriptionId={}, userId={}",
                        localStatus.stripeSubscriptionId(),
                        userId
                );
                return withoutPaymentAction(localStatus, null, null, null);
            }

            String clientSecret = invoiceConfirmationSecret(stripeSubscription);
            String hostedInvoiceUrl = invoiceHostedUrl(stripeSubscription);
            String paymentMethodId = trimmed(stripeSubscription.getDefaultPaymentMethod());
            if (paymentMethodId == null && customerId != null) {
                Customer customer = stripeClient.v1().customers().retrieve(customerId);
                paymentMethodId = customer.getInvoiceSettings() == null
                        ? null
                        : trimmed(customer.getInvoiceSettings().getDefaultPaymentMethod());
            }

            // Webhooks are the primary state transition mechanism, but a delayed or
            // misconfigured webhook must not leave a successfully authenticated payment in
            // "incomplete" forever. Reconcile the local row from Stripe when the status changed.
            String stripeStatus = trimmed(stripeSubscription.getStatus());
            if (stripeStatus != null && !stripeStatus.equals(localStatus.status())) {
                try {
                    subscriptionService.saveFromStripe(
                            stripeSubscription,
                            userId,
                            paymentMethodId
                    );
                    SubscriptionStatusResponse refreshed = subscriptionService.findStatus(userId);
                    if (!"incomplete".equals(refreshed.status())) {
                        return refreshed;
                    }
                    localStatus = refreshed;
                } catch (RuntimeException reconciliationException) {
                    log.error(
                            "Could not reconcile the local subscription from Stripe: " +
                                    "subscriptionId={}, userId={}, stripeStatus={}",
                            localStatus.stripeSubscriptionId(),
                            userId,
                            stripeStatus,
                            reconciliationException
                    );
                }
            }

            return new SubscriptionStatusResponse(
                    localStatus.premium(),
                    localStatus.creationBlocked(),
                    localStatus.status(),
                    localStatus.subscriptionId(),
                    localStatus.stripeSubscriptionId(),
                    localStatus.priceId(),
                    localStatus.startDate(),
                    localStatus.endDate(),
                    localStatus.cancelAtPeriodEnd(),
                    invoiceRequiresCustomerAction(stripeSubscription, clientSecret),
                    clientSecret,
                    hostedInvoiceUrl,
                    paymentMethodId
            );
        } catch (StripeException exception) {
            if (isAuthenticationFailure(exception)) {
                throw new StripeConfigurationException(
                        "Stripe credentials are invalid or do not grant access to the configured account.",
                        exception
                );
            }
            // The local status is still useful, but the client will see that the action could not
            // be prepared instead of receiving a fake success or an empty secret.
            log.error(
                    "Failed to retrieve the incomplete Stripe subscription for payment action: " +
                            "subscriptionId={}, userId={}",
                    localStatus.stripeSubscriptionId(),
                    userId,
                    exception
            );
            return withoutPaymentAction(localStatus, null, null, null);
        }
    }

    /**
     * Builds a status response for a subscription whose payment action could not be prepared.
     *
     * <p>No usable confirmation secret exists in that situation, so the client must not be told
     * that a customer authentication step is pending.</p>
     */
    private SubscriptionStatusResponse withoutPaymentAction(
            SubscriptionStatusResponse status,
            String clientSecret,
            String hostedInvoiceUrl,
            String paymentMethodId
    ) {
        return new SubscriptionStatusResponse(
                status.premium(),
                status.creationBlocked(),
                status.status(),
                status.subscriptionId(),
                status.stripeSubscriptionId(),
                status.priceId(),
                status.startDate(),
                status.endDate(),
                status.cancelAtPeriodEnd(),
                false,
                clientSecret,
                hostedInvoiceUrl,
                paymentMethodId
        );
    }

    /**
     * Decides whether the customer still has to authenticate the first invoice.
     *
     * <p>The PaymentIntent status is the only trustworthy signal. An "incomplete" subscription is
     * not the same thing as an SCA challenge: the payment may never have been attempted, or it
     * may have failed, and neither of those is a 3D Secure step. When the PaymentIntent cannot
     * be read, the presence of a usable confirmation secret is used as a fallback, because the
     * client can still finish the payment with it.</p>
     */
    private boolean invoiceRequiresCustomerAction(Subscription subscription, String clientSecret) {
        PaymentIntent paymentIntent = retrieveInvoicePaymentIntent(subscription);
        if (paymentIntent != null) {
            return "requires_action".equals(trimmed(paymentIntent.getStatus()));
        }
        if (clientSecret != null) {
            log.warn(
                    "Could not determine the Stripe PaymentIntent state of the incomplete "
                            + "subscription; assuming a customer authentication step is pending: "
                            + "subscriptionId={}",
                    subscription == null ? null : subscription.getId()
            );
        }
        return clientSecret != null;
    }

    /**
     * Retrieves the PaymentIntent that belongs to the invoice's confirmation secret.
     *
     * <p>The invoice no longer exposes the PaymentIntent directly; its id is encoded in the
     * confirmation secret ({@code pi_<id>_secret_<secret>}).</p>
     */
    private PaymentIntent retrieveInvoicePaymentIntent(Subscription subscription) {
        String clientSecret = invoiceConfirmationSecret(subscription);
        if (clientSecret == null) {
            return null;
        }
        String paymentIntentId = paymentIntentIdFrom(clientSecret);
        if (paymentIntentId == null) {
            return null;
        }
        try {
            return stripeClient.v1().paymentIntents().retrieve(paymentIntentId);
        } catch (StripeException exception) {
            log.warn(
                    "Could not read the Stripe PaymentIntent state of the incomplete subscription: "
                            + "subscriptionId={}, paymentIntentId={}",
                    subscription == null ? null : subscription.getId(),
                    paymentIntentId,
                    exception
            );
            return null;
        }
    }

    private String paymentIntentIdFrom(String clientSecret) {
        String value = trimmed(clientSecret);
        if (value == null || !value.startsWith(PAYMENT_INTENT_CLIENT_SECRET_PREFIX)) {
            return null;
        }
        String remainder = value.substring(PAYMENT_INTENT_CLIENT_SECRET_PREFIX.length());
        int separator = remainder.indexOf(PAYMENT_INTENT_SECRET_SUFFIX);
        if (separator <= 0) {
            return null;
        }
        return PAYMENT_INTENT_CLIENT_SECRET_PREFIX + remainder.substring(0, separator);
    }

    private String invoiceConfirmationSecret(Subscription subscription) {
        if (subscription == null) {
            return null;
        }
        Invoice invoice = subscription.getLatestInvoiceObject();
        if (invoice == null || invoice.getConfirmationSecret() == null) {
            return null;
        }
        return trimmed(invoice.getConfirmationSecret().getClientSecret());
    }

    private String invoiceStatus(Subscription subscription) {
        if (subscription == null) {
            return null;
        }
        Invoice invoice = subscription.getLatestInvoiceObject();
        return invoice == null ? null : trimmed(invoice.getStatus());
    }

    private String invoiceHostedUrl(Subscription subscription) {
        if (subscription == null) {
            return null;
        }
        Invoice invoice = subscription.getLatestInvoiceObject();
        return invoice == null ? null : trimmed(invoice.getHostedInvoiceUrl());
    }

    @Override
    public List<SubscriptionResponse> getSubscriptionHistory(Long userId) {
        requireActiveUser(userId);
        return subscriptionService.findSubscriptionHistory(userId);
    }

    @Override
    public SubscriptionResponse cancelSubscription(Long userId, boolean immediately) {
        User user = requireActiveUser(userId);

        SubscriptionResponse current = subscriptionService.findActiveSubscription(userId)
                .or(() -> subscriptionService.findBlockingSubscription(userId))
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        "No cancellable subscription found for user " + userId
                ));

        if (!userId.equals(current.userId())) {
            throw new IllegalStateException("Subscription ownership check failed");
        }

        try {
            Subscription changed;
            // Incomplete/unpaid/paused subscriptions have no meaningful period end, so an
            // at-period-end request must still terminate them immediately.
            boolean effectiveImmediately = immediately
                    || !SubscriptionStatusPolicy.isEntitled(current.status());
            if (effectiveImmediately) {
                changed = stripeClient.v1().subscriptions().cancel(current.stripeSubscriptionId());
            } else {
                changed = stripeClient.v1().subscriptions().update(
                        current.stripeSubscriptionId(),
                        SubscriptionUpdateParams.builder()
                                .setCancelAtPeriodEnd(true)
                                .build()
                );
            }

            if (changed.getCustomer() != null
                    && user.getStripeCustomerId() != null
                    && !changed.getCustomer().equals(user.getStripeCustomerId())) {
                throw new IllegalStateException(
                        "Stripe subscription does not belong to the user's customer"
                );
            }

            log.info(
                    "Stripe subscription cancellation applied: subscriptionId={}, userId={}, immediately={}",
                    changed.getId(),
                    userId,
                    immediately
            );
            return subscriptionService.saveFromStripe(changed, userId, null);
        } catch (StripeException e) {
            log.error(
                    "Failed to cancel Stripe subscription: subscriptionId={}, userId={}",
                    current.stripeSubscriptionId(),
                    userId,
                    e
            );
            throw new StripeOperationException("Failed to cancel subscription", e);
        }
    }

    private User requireActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
