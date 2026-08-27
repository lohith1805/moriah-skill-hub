package com.moriah.skillhub.user.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per user, created lazily on first access rather than at registration (build-plan.md
 * feature 09 gives no "created at registration" instruction, unlike {@code user_roles}) — see
 * {@code ProfileService.getOrCreateProfile}.
 * <p>
 * {@code skills}/{@code education}/{@code workExperience} store pre-serialized JSON text, same
 * treatment as {@code Notification.payload}/{@code WebhookEvent.payload} — {@code ProfileService}
 * is the only thing that parses or writes them; nothing queries into them.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(length = 150)
    private String location;

    @Column(name = "current_title", length = 150)
    private String currentTitle;

    @Column(name = "experience_level", length = 30)
    private String experienceLevel;

    // columnDefinition matches V1's TINYINT UNSIGNED exactly — see SubscriptionPlan (feature 07)
    // for why a plain Integer mapping fails ddl-auto: validate against an UNSIGNED column.
    @Column(name = "years_experience", columnDefinition = "TINYINT UNSIGNED")
    private Integer yearsExperience;

    @Column(columnDefinition = "JSON")
    private String skills;

    @Column(columnDefinition = "JSON")
    private String education;

    @Column(name = "work_experience", columnDefinition = "JSON")
    private String workExperience;

    @Column(name = "resume_key", length = 255)
    private String resumeKey;

    @Column(name = "portfolio_slug", unique = true, length = 150)
    private String portfolioSlug;

    @Column(name = "is_complete", nullable = false)
    private boolean complete = false;

    // columnDefinition matches V1's TINYINT UNSIGNED exactly.
    @Column(name = "completion_percent", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private int completionPercent = 0;
}
