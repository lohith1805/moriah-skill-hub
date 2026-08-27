package com.moriah.skillhub.attendance.entity;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.common.util.Constants;
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

import java.time.Instant;

/**
 * {@code batch} is a real {@code @ManyToOne Batch} — the established shared-kernel exception
 * ({@code Sprint}'s Javadoc). {@code sprintId} stays a bare {@code Long}, not {@code @ManyToOne
 * Sprint} — matches {@code WeeklyReview.sprintId} (feature 12), the direct precedent for an
 * optional, less-central cross-package link, rather than {@code Sprint}/{@code Task}'s own
 * same-package association. {@code conductedBy} is nullable: a standup this job auto-conducts
 * (see {@code AttendanceFinalisationJob}) never had a PM click anything.
 * <p>
 * No endpoint in build-plan.md's feature 13 list ever sets {@code status = CONDUCTED} directly —
 * {@code AttendanceFinalisationJob} is the only writer of that transition (`/architect feature
 * 13` decision), promoting any non-cancelled {@code SCHEDULED} standup whose {@code scheduledAt}
 * has passed, in the same nightly pass that finalises it. This guarantees the 75% rule's safety
 * net fires even on a day nobody checks in and no PM touches the record — the exact case the job
 * exists to catch. {@code PUT /standups/{id}} is the only other writer, and only ever moves
 * {@code SCHEDULED -> CANCELLED}.
 */
@Entity
@Table(name = "standups")
@Getter
@Setter
@NoArgsConstructor
public class Standup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(name = "sprint_id")
    private Long sprintId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    // SMALLINT UNSIGNED in V7 — plain Integer defaults to signed INTEGER and fails
    // ddl-auto: validate (the recurring UNSIGNED/Hibernate mismatch first documented on
    // SubscriptionPlan.tierRank, feature 07; see Sprint.sprintNumber for the TINYINT variant).
    @Column(name = "late_cutoff_minutes", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer lateCutoffMinutes = Constants.ATTENDANCE_DEFAULT_LATE_CUTOFF_MINUTES;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by")
    private User conductedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StandupStatus status = StandupStatus.SCHEDULED;

    @Column(name = "finalised_at")
    private Instant finalisedAt;
}
