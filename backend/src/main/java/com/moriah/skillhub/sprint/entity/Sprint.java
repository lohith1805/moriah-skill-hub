package com.moriah.skillhub.sprint.entity;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * {@code batch} is a real {@code @ManyToOne Batch} — a deliberate second shared-kernel exception
 * alongside {@code User} (see {@code Batch}'s own Javadoc for the first). This is not a judgment
 * call made from scratch: code-standards.md's own canonical {@code Sprint}/{@code SprintRepository}
 * /{@code SprintService} examples — written to teach this project's conventions using this exact
 * feature — literally have {@code @ManyToOne Batch batch} and {@code s.batch.id = :batchId} in a
 * JPQL projection. Feature 11 is what makes those examples real, so they're followed as given
 * rather than re-litigated into a bare {@code Long batchId} the way {@code Payment.planId}/
 * {@code Batch.planTierMinId} are for cross-package associations with no such canonical example.
 * No validation annotations here (code-standards.md "Entity") — see {@code CreateSprintRequest}/
 * {@code UpdateSprintRequest} for the 1-2-week-duration and non-overlap rules.
 */
@Entity
@Table(name = "sprints", uniqueConstraints =
    @UniqueConstraint(columnNames = {"batch_id", "sprint_number"}))
@Getter
@Setter
@NoArgsConstructor
public class Sprint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    // TINYINT UNSIGNED in V6 — plain Integer defaults to signed INTEGER and fails
    // ddl-auto: validate (the recurring UNSIGNED/Hibernate mismatch first documented on
    // SubscriptionPlan.tierRank, feature 07).
    @Column(name = "sprint_number", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer sprintNumber;

    @Column(columnDefinition = "TEXT")
    private String goal;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SprintStatus status = SprintStatus.PLANNED;

    @Column(name = "planned_points", columnDefinition = "SMALLINT UNSIGNED")
    private Integer plannedPoints;

    @Column(name = "completed_points", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer completedPoints = 0;
}
