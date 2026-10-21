package com.example.twitter_challenge.Service;

import com.example.twitter_challenge.Entity.Subscription;
import com.example.twitter_challenge.Entity.User;
import com.example.twitter_challenge.Repository.SubscriptionRepository;
import com.example.twitter_challenge.Repository.UserRepository;
import com.example.twitter_challenge.Service.interfaces.SubscriptionService;
import com.example.twitter_challenge.dto.response.SubscriptionResponse;
import com.example.twitter_challenge.dto.response.SubscriptionStatusResponse;
import com.example.twitter_challenge.exception.UserNotFoundException;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.SubscriptionItemCollection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Value("${stripe.premium-price-id}")
    private String premiumPriceId;

    public SubscriptionServiceImpl(
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    @Override
    public SubscriptionResponse saveFromStripe(
            com.stripe.model.Subscription stripeSubscription,
            Long userId,
            String defaultPaymentMethodId
    ) {
        return saveFromStripe(stripeSubscription, userId, defaultPaymentMethodId, null);
    }

    @Transactional
    @Override
    public SubscriptionResponse saveFromStripe(
            com.stripe.model.Subscription stripeSubscription,
            Long userId,
            String defaultPaymentMethodId,
            Date stripeEventCreatedAt
    ) {
        if (stripeSubscription == null) {
            throw new IllegalArgumentException("stripeSubscription must not be null");
        }
        requireUserId(userId);

        String stripeSubscriptionId = requireValue(
                stripeSubscription.getId(),
                "Stripe subscription id"
        );

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));

        if (user.getStripeCustomerId() != null
                && !user.getStripeCustomerId().isBlank()
                && !user.getStripeCustomerId().equals(stripeSubscription.getCustomer())) {
            throw new IllegalStateException(
                    "Stripe subscription customer does not match the user's customer"
            );
        }

        Subscription subscription = subscriptionRepository
                .findByStripeSubscriptionId(stripeSubscriptionId)
                .orElseGet(Subscription::new);

        boolean isNew = subscription.getId() == null;

        if (!isNew && !userId.equals(subscription.getUser().getId())) {
            throw new IllegalStateException(
                    "The Stripe subscription already belongs to another user"
            );
        }

        if (!isNew && isOlderThanAppliedEvent(subscription, stripeEventCreatedAt)) {
            log.info(
                    "Ignoring out-of-order Stripe event: subscriptionId={}, eventCreatedAt={}, lastApplied={}",
                    stripeSubscriptionId,
                    stripeEventCreatedAt,
                    subscription.getLastStripeEventCreatedAt()
            );
            return mapToResponse(subscription);
        }

        applyStripeData(subscription, stripeSubscription, user, defaultPaymentMethodId);
        // Direct API responses have no webhook timestamp. Record the local application time so a
        // delayed webhook generated before this write cannot roll the state backwards.
        subscription.setLastStripeEventCreatedAt(
                stripeEventCreatedAt == null ? new Date() : stripeEventCreatedAt
        );

        Subscription saved = subscriptionRepository.save(subscription);
        log.info(
                "Stripe subscription persisted: subscriptionId={}, userId={}, status={}, new={}",
                saved.getStripeSubscriptionId(),
                userId,
                saved.getSubscriptionStatus(),
                isNew
        );
        return mapToResponse(saved);
    }

    private boolean isOlderThanAppliedEvent(Subscription subscription, Date eventCreatedAt) {
        return eventCreatedAt != null
                && subscription.getLastStripeEventCreatedAt() != null
                && eventCreatedAt.before(subscription.getLastStripeEventCreatedAt());
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<SubscriptionResponse> findActiveSubscription(Long userId) {
        requireUserId(userId);
        return subscriptionRepository
                .findFirstByUserIdAndSubscriptionStatusInAndIsDeletedFalseOrderByCreatedAtDesc(
                        userId,
                        SubscriptionStatusPolicy.entitledStatuses()
                )
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<SubscriptionResponse> findBlockingSubscription(Long userId) {
        requireUserId(userId);
        return subscriptionRepository
                .findFirstByUserIdAndSubscriptionStatusInAndIsDeletedFalseOrderByCreatedAtDesc(
                        userId,
                        SubscriptionStatusPolicy.creationBlockingStatuses()
                )
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<SubscriptionResponse> findLatestSubscription(Long userId) {
        requireUserId(userId);
        return subscriptionRepository.findFirstByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public SubscriptionStatusResponse findStatus(Long userId) {
        requireUserId(userId);
        Optional<Subscription> latest =
                subscriptionRepository.findFirstByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
        boolean premium = subscriptionRepository.existsByUserIdAndSubscriptionStatusInAndIsDeletedFalse(
                userId,
                SubscriptionStatusPolicy.entitledStatuses()
        );
        boolean blocked = subscriptionRepository.existsByUserIdAndSubscriptionStatusInAndIsDeletedFalse(
                userId,
                SubscriptionStatusPolicy.creationBlockingStatuses()
        );

        if (latest.isEmpty()) {
            return new SubscriptionStatusResponse(
                    false, false, "none", null, null, null, null, null, false,
                    false, null, null, null
            );
        }

        Subscription subscription = latest.get();
        return new SubscriptionStatusResponse(
                premium,
                blocked,
                subscription.getSubscriptionStatus(),
                subscription.getId(),
                subscription.getStripeSubscriptionId(),
                subscription.getPriceId(),
                subscription.getStartDate(),
                subscription.getEndDate(),
                Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()),
                false,
                null,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    @Override
    public List<SubscriptionResponse> findSubscriptionHistory(Long userId) {
        requireUserId(userId);
        return subscriptionRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasActiveSubscription(Long userId) {
        requireUserId(userId);
        return subscriptionRepository.existsByUserIdAndSubscriptionStatusInAndIsDeletedFalse(
                userId,
                SubscriptionStatusPolicy.entitledStatuses()
        );
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasBlockingSubscription(Long userId) {
        requireUserId(userId);
        return subscriptionRepository.existsByUserIdAndSubscriptionStatusInAndIsDeletedFalse(
                userId,
                SubscriptionStatusPolicy.creationBlockingStatuses()
        );
    }

    @Transactional(readOnly = true)
    @Override
    public long nextCreationGeneration(Long userId) {
        requireUserId(userId);
        return subscriptionRepository.countByUserId(userId) + 1L;
    }

    @Transactional(readOnly = true)
    @Override
    public Set<Long> findPremiumUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<Long> entitledUserIds = subscriptionRepository.findEntitledUserIds(
                ids,
                SubscriptionStatusPolicy.entitledStatuses()
        );
        if (entitledUserIds == null || entitledUserIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(entitledUserIds);
    }

    private void applyStripeData(
            Subscription target,
            com.stripe.model.Subscription source,
            User user,
            String fallbackPaymentMethodId
    ) {
        SubscriptionItem item = firstSubscriptionItemOrNull(source);
        String priceId = item != null && item.getPrice() != null ? item.getPrice().getId() : null;

        if (priceId == null && (target.getPriceId() == null || target.getPriceId().isBlank())) {
            // Deleted subscription events can omit items. This application sells a single
            // premium price, so retain a valid local price instead of rejecting the event.
            priceId = premiumPriceId;
        }

        target.setUser(user);
        target.setStripeSubscriptionId(requireValue(source.getId(), "Stripe subscription id"));
        target.setStripeCustomerId(requireValue(source.getCustomer(), "Stripe customer id"));
        target.setSubscriptionStatus(requireValue(source.getStatus(), "Stripe subscription status"));
        if (priceId != null) {
            target.setPriceId(priceId);
        }
        if (item != null) {
            target.setStartDate(toDate(item.getCurrentPeriodStart()));
            target.setEndDate(toDate(item.getCurrentPeriodEnd()));
        }
        target.setCancelAtPeriodEnd(Boolean.TRUE.equals(source.getCancelAtPeriodEnd()));
        target.setDefaultPaymentMethodId(
                resolveDefaultPaymentMethodId(source, fallbackPaymentMethodId, target.getDefaultPaymentMethodId())
        );
    }

    private SubscriptionItem firstSubscriptionItemOrNull(com.stripe.model.Subscription source) {
        SubscriptionItemCollection items = source.getItems();
        if (items == null || items.getData() == null || items.getData().isEmpty()) {
            return null;
        }
        return items.getData().get(0);
    }

    private String resolveDefaultPaymentMethodId(
            com.stripe.model.Subscription source,
            String fallbackPaymentMethodId,
            String existingPaymentMethodId
    ) {
        String subscriptionDefault = source.getDefaultPaymentMethod();
        if (subscriptionDefault != null && !subscriptionDefault.isBlank()) {
            return subscriptionDefault;
        }
        if (fallbackPaymentMethodId != null && !fallbackPaymentMethodId.isBlank()) {
            return fallbackPaymentMethodId;
        }
        return existingPaymentMethodId;
    }

    private Date toDate(Long epochSeconds) {
        return epochSeconds == null ? null : new Date(epochSeconds * 1000L);
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " is missing on the Stripe subscription");
        }
        return value;
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
    }

    private SubscriptionResponse mapToResponse(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getUser().getId(),
                subscription.getStripeSubscriptionId(),
                subscription.getSubscriptionStatus(),
                subscription.getPriceId(),
                subscription.getStartDate(),
                subscription.getEndDate(),
                Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())
        );
    }
}
