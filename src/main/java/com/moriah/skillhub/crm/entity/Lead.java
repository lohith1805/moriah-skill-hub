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

    // columnDefinition matches V12's CHAR(64) exactly — a SHA-256 hex digest is always exactly
    // 64 characters, and Hibernate's default for a String column is VARCHAR, not CHAR (same fix
    // as RefreshToken.tokenHash).
    @Column(name = "dedupe_hash", nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String dedupeHash;
}
