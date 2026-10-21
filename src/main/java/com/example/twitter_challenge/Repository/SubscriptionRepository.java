package com.example.twitter_challenge.Repository;

import com.example.twitter_challenge.Entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    List<Subscription> findByStripeCustomerIdOrderByCreatedAtDesc(String stripeCustomerId);

    Optional<Subscription> findFirstByStripeCustomerIdAndSubscriptionStatusInAndIsDeletedFalseOrderByCreatedAtDesc(
            String stripeCustomerId,
            Collection<String> subscriptionStatuses
    );

    Optional<Subscription> findFirstByUserIdAndSubscriptionStatusInAndIsDeletedFalseOrderByCreatedAtDesc(
            Long userId,
            Collection<String> subscriptionStatuses
    );

    Optional<Subscription> findFirstByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndSubscriptionStatusInAndIsDeletedFalse(
            Long userId,
            Collection<String> subscriptionStatuses
    );

    List<Subscription> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(Long userId);

    List<Subscription> findByUserId(Long userId);

    long countByUserId(Long userId);

    List<Subscription> findByUserIdAndSubscriptionStatus(Long userId, String subscriptionStatus);

    boolean existsByStripeSubscriptionId(String stripeSubscriptionId);

    @Query("""
            select distinct s.user.id
            from Subscription s
            where s.user.id in :userIds
              and s.subscriptionStatus in :subscriptionStatuses
              and (s.isDeleted = false or s.isDeleted is null)
            """)
    List<Long> findEntitledUserIds(
            @Param("userIds") Collection<Long> userIds,
            @Param("subscriptionStatuses") Collection<String> subscriptionStatuses
    );
}
