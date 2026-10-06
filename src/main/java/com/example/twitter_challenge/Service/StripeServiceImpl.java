package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.StripeService;
import com.example.twitter_challenge.exception.UserNotFoundException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.param.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StripeServiceImpl implements StripeService {

    private final StripeClient stripeClient;
    private final UserRepository userRepository;

    @Value("${stripe.premium-price-id}")
    private String premiumPriceId;

    public StripeServiceImpl( StripeClient stripeClient, UserRepository userRepository ) {
        this.stripeClient = stripeClient;
        this.userRepository = userRepository;
    }

    @Override
    public String createCustomer(String email, String username) {
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .setName(username)
                .build();

        try {
            Customer response = stripeClient.v1().customers().create(params);
            User user = userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found"));
            user.setStripeCustomerId(response.getId());
            userRepository.save(user);
            log.info("Stripe customer created: customerId={}, username={}", response.getId(), username);
            return user.getStripeCustomerId();
        } catch (StripeException e) {
            log.error(
                    "Failed to create Stripe customer: username={}",
                    username,
                    e
            );
            throw new RuntimeException("Failed to create Stripe customer", e);
        }
    }

    @Override
    public String createSubscription(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String customerId = user.getStripeCustomerId();

        if (customerId == null || customerId.isBlank()) {
            throw new IllegalStateException("User has no Stripe customer");
        }


        try {
            Customer customer = stripeClient.v1()
                    .customers()
                    .retrieve(customerId);

            String defaultPaymentMethod = customer.getInvoiceSettings()
                    .getDefaultPaymentMethod();

            if (defaultPaymentMethod == null) {
                throw new IllegalStateException(
                        "Customer has no default payment method"
                );
            }

            SubscriptionCreateParams.Item item =
                    SubscriptionCreateParams.Item.builder()
                            .setPrice(premiumPriceId)
                            .build();

            SubscriptionCreateParams params =
                    SubscriptionCreateParams.builder()
                            .setCustomer(customerId)
                            .addItem(item)
                            .setDefaultPaymentMethod(defaultPaymentMethod)
                            .build();

            Subscription subscription = stripeClient.v1()
                    .subscriptions()
                    .create(params);
            log.info(
                    "Stripe subscription created: subscriptionId={}, customerId={}",
                    subscription.getId(),
                    customerId
            );

            return subscription.getId();

        } catch (StripeException e) {
            log.error(
                    "Failed to create Stripe subscription: customerId={}",
                    customerId,
                    e
            );
            throw new RuntimeException("Failed to create subscription", e);
        }
    }

    @Override
    public void attachPaymentMethod(Long userId, String paymentMethodId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String customerId = user.getStripeCustomerId();

        if (customerId == null || customerId.isBlank()) {
            throw new IllegalStateException("User has no Stripe customer");
        }

        try {
            PaymentMethod paymentMethod = stripeClient.v1()
                    .paymentMethods()
                    .retrieve(paymentMethodId);

            String attachedCustomerId = paymentMethod.getCustomer();

            if (attachedCustomerId == null) {

                PaymentMethodAttachParams attachParams =
                        PaymentMethodAttachParams.builder()
                                .setCustomer(customerId)
                                .build();

                stripeClient.v1()
                        .paymentMethods()
                        .attach(paymentMethodId, attachParams);

            } else if (!customerId.equals(attachedCustomerId)) {

                throw new IllegalStateException(
                        "PaymentMethod belongs to another customer"
                );
            }

            CustomerUpdateParams updateParams =
                    CustomerUpdateParams.builder()
                            .setInvoiceSettings(
                                    CustomerUpdateParams.InvoiceSettings.builder()
                                            .setDefaultPaymentMethod(paymentMethodId)
                                            .build()
                            )
                            .build();

            stripeClient.v1()
                    .customers()
                    .update(customerId, updateParams);
            log.info(
                    "Stripe payment method attached: customerId={}",
                    customerId
            );

        } catch (StripeException e) {
            log.error(
                    "Failed to attach Stripe payment method: customerId={}",
                    customerId,
                    e
            );
            throw new RuntimeException("Failed to attach payment method", e);
        }
    }

    @Override
    public String createSetupIntent(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        String customerId = user.getStripeCustomerId();
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalStateException("User has no Stripe customer");
        }
        try {
            SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                    .setCustomer(customerId)
                    .addPaymentMethodType("card")
                    .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                    .build();

            SetupIntent setupIntent = stripeClient.v1()
                    .setupIntents()
                    .create(params);
            log.info(
                    "Stripe SetupIntent created: setupIntentId={}, customerId={}",
                    setupIntent.getId(),
                    customerId
            );

            return setupIntent.getClientSecret();

        } catch (StripeException e) {
            log.error(
                    "Failed to create SetupIntent: customerId={}",
                    customerId,
                    e
            );
            throw new RuntimeException("Failed to create SetupIntent", e);
        }

    }
}



