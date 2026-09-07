package com.moriah.skillhub.subscription.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Seeded by {@code V5__seed_plans.sql} (feature 02) — this feature only reads it. {@code
 * tier_rank} and the {@code allows_*}/{@code mentor_support} flags are what {@link
 * com.moriah.skillhub.common.security.EntitlementFlagsLoader} (feature 04) already reads;
 * nothing here duplicates that.
 */
@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlan extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "price_inr", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceInr;

    // columnDefinition matches V3's TINYINT/SMALLINT UNSIGNED exactly — Hibernate's default for
    // an Integer column is plain INTEGER, which ddl-auto: validate treats as a mismatch against
    // any UNSIGNED narrower-than-BIGINT column (the same class of fix as feature 03's CHAR vs
    // VARCHAR default; BIGINT UNSIGNED FK/id columns don't need this override, only these do).
    @Column(name = "tier_rank", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer tierRank;

    @Column(name = "duration_days", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer durationDays;

    @Column(name = "max_projects", columnDefinition = "SMALLINT UNSIGNED")
    private Integer maxProjects;

    @Column(name = "mentor_support", nullable = false)
    private boolean mentorSupport;

    @Column(name = "allows_batch", nullable = false)
    private boolean allowsBatch;

    @Column(name = "allows_sprints", nullable = false)
    private boolean allowsSprints;

    @Column(name = "allows_pip", nullable = false)
    private boolean allowsPip;

    @Column(name = "allows_internship_letter", nullable = false)
    private boolean allowsInternshipLetter;

    @Column(name = "allows_client_project", nullable = false)
    private boolean allowsClientProject;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
