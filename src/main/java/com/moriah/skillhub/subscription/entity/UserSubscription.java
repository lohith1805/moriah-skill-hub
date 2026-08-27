package com.moriah.skillhub.subscription.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * {@code plan} is a real {@code @ManyToOne} — {@link SubscriptionPlan} is in this same {@code
 * subscription/} package. {@code paymentId} is a plain id, not a {@code @ManyToOne Payment} —
 * {@code Payment} belongs to the sibling {@code payment/} package (architecture.md's package
 * diagram); same reasoning as {@code Payment.planId}'s Javadoc, in the other direction.
 * <p>
 * {@code active_user_id} (V3's generated column enforcing one ACTIVE subscription per user) is
 * database-only — nothing here needs to read or write it directly, Hibernate never sees it.
 */
@Entity
@Table(name = "user_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class UserSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id")
    private SubscriptionPlan plan;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;
}
