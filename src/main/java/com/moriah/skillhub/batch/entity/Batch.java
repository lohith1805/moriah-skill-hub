package com.moriah.skillhub.batch.entity;

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
 * {@code pm} is a real {@code @ManyToOne User} — {@code User} is shared-kernel identity data
 * across every feature package (the established exception; see {@code Payment}'s Javadoc for the
 * contrasting bare-id treatment of {@code SubscriptionPlan}). {@code planTierMinId} is a bare
 * {@code Long}, not {@code @ManyToOne SubscriptionPlan} — same cross-package reasoning as {@code
 * Payment.planId}. No {@code version} column, deliberately — capacity is enforced by {@code
 * BatchRepository}'s conditional atomic update, not optimistic locking (code-standards.md
 * "Transactions").
 */
@Entity
@Table(name = "batches")
@Getter
@Setter
@NoArgsConstructor
public class Batch extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "track_code", nullable = false, length = 30)
    private String trackCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pm_id")
    private User pm;

    @Column(name = "plan_tier_min_id")
    private Long planTierMinId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // columnDefinition matches V6's SMALLINT UNSIGNED exactly — see SubscriptionPlan for why a
    // plain Integer mapping fails ddl-auto: validate against an UNSIGNED column.
    @Column(nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer capacity;

    @Column(name = "enrolled_count", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer enrolledCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status = BatchStatus.PLANNED;
}
