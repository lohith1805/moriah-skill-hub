package com.moriah.skillhub.crm.entity;

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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code interestedPlanId} is a bare FK, not a real {@code @ManyToOne} to {@code SubscriptionPlan}
 * — same reasoning as {@code PendingBatchAllocation.planId} (architecture.md's shared-kernel
 * exception covers only {@code User}/{@code Batch}). {@code assignedAgent}/{@code convertedUser}
 * are real associations to {@code User}, the shared kernel itself.
 */
@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
public class Lead extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadSource source;

    @Column(name = "lead_type", nullable = false, length = 50)
    private String leadType;

    @Column(length = 150)
    private String institution;

    @Column(name = "interested_plan_id")
    private Long interestedPlanId;

    /** Per-lead estimated / closed deal amount. Nullable — see V34's own comment. */
    @Column(name = "deal_value", precision = 12, scale = 2)
    private BigDecimal dealValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private User assignedAgent;

    @Column(name = "lost_reason", length = 500)
    private String lostReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_user_id")
    private User convertedUser;

    /** Denormalised cache of the latest follow-up date across this lead's activities — set by
     * {@code LeadService.addActivity} so the board view doesn't N+1 over {@code lead_activities}. */
    @Column(name = "next_follow_up_at")
    private Instant nextFollowUpAt;

    /** Soft delete — set by {@code DELETE /api/v1/leads/{id}}; every list query filters it out. */
    @Column(name = "archived_at")
    private Instant archivedAt;

    // columnDefinition matches V12's CHAR(64) exactly — a SHA-256 hex digest is always exactly
    // 64 characters, and Hibernate's default for a String column is VARCHAR, not CHAR (same fix
    // as RefreshToken.tokenHash).
    @Column(name = "dedupe_hash", nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String dedupeHash;
}
