package com.example.twitter_challenge.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(schema = "public", name = "subscriptions")
public class Subscription extends EntityBase {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "stripe_subscription_id", unique = true, nullable = false)
    private String stripeSubscriptionId;

    @Column(name = "stripe_customer_id", nullable = false)
    private String stripeCustomerId;

    @Column(name = "subscription_status", nullable = false)
    private String subscriptionStatus; // active, past_due, canceled, unpaid, incomplete

    @Column(name = "price_id", nullable = false)
    private String priceId;

    @Column(name = "start_date")
    private Date startDate;

    @Column(name = "end_date")
    private Date endDate;

    @Column(name = "cancel_at_period_end")
    private Boolean cancelAtPeriodEnd = false;

    @Column(name = "default_payment_method_id")
    private String defaultPaymentMethodId;

    /**
     * Creation time (in seconds, as a timestamp) of the newest Stripe webhook event applied to
     * this row. Older events are ignored so out-of-order delivery cannot roll state backwards.
     */
    @Column(name = "last_stripe_event_created_at")
    private Date lastStripeEventCreatedAt;

    @PrePersist
    protected void prePersist() {
        Date now = new Date();

        if (getCreatedAt() == null) {
            setCreatedAt(now);
        }
        setUpdatedAt(now);

        // EntityBase.isDeleted has no Java-side default; subscriptions.is_deleted is NOT NULL in the DB.
        if (getIsDeleted() == null) {
            setIsDeleted(false);
        }
        if (getCancelAtPeriodEnd() == null) {
            setCancelAtPeriodEnd(false);
        }
    }

    @PreUpdate
    protected void preUpdate() {
        setUpdatedAt(new Date());
    }

}
