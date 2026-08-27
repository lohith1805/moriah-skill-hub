package com.moriah.skillhub.batch.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * `/architect feature 10`: the persisted "pending queue" — build-plan.md feature 10's "no
 * matching batch -> pending queue, PM notified. Never silently unallocated" has nowhere to live
 * otherwise, since {@code batch_students.batch_id} is {@code NOT NULL}. {@code planId} is a bare
 * {@code Long} ({@code SubscriptionPlan} is a sibling {@code subscription/} package — same
 * reasoning as {@code Payment.planId}); {@code resolvedBatch} is a real {@code @ManyToOne} since
 * {@link Batch} lives in this same package. V9's {@code open_user_id} generated column (one open
 * row per user) is database-only, same treatment as {@code UserSubscription.active_user_id} —
 * Hibernate never sees it.
 */
@Entity
@Table(name = "pending_batch_allocations")
@Getter
@Setter
@NoArgsConstructor
public class PendingBatchAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "track_code", nullable = false, length = 30)
    private String trackCode;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(length = 255)
    private String reason;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_batch_id")
    private Batch resolvedBatch;
}
