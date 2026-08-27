package com.moriah.skillhub.metrics.entity;

import com.moriah.skillhub.batch.entity.Batch;
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

import java.math.BigDecimal;
import java.time.Instant;

/** {@code user}/{@code batch} are real {@code @ManyToOne} associations — the shared-kernel
 * exception architecture.md grants only {@code User} and {@code Batch} (every other cross-package
 * reference in this codebase stays a bare {@code Long} id). Every row is written by {@code
 * StudentMetricsService#refresh} through a plain {@code JdbcTemplate} upsert, never through this
 * entity's own repository (code-standards.md "Native SQL only for... metrics upserts") — this
 * mapping exists for the read side {@code PipEvaluationJob} (feature 17) needs. */
@Entity
@Table(name = "student_metrics")
@Getter
@Setter
@NoArgsConstructor
public class StudentMetric extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    // Plain (signed) SMALLINT in V10, not SMALLINT UNSIGNED — still needs the explicit
    // columnDefinition override: Hibernate's default mapping for a Java Integer is plain INTEGER,
    // which `ddl-auto: validate` rejects against any narrower column width, signed or not (see
    // Standup.lateCutoffMinutes' Javadoc for the UNSIGNED case of the same underlying mismatch).
    @Column(name = "attendance_present", nullable = false, columnDefinition = "SMALLINT")
    private Integer attendancePresent = 0;

    @Column(name = "attendance_total", nullable = false, columnDefinition = "SMALLINT")
    private Integer attendanceTotal = 0;

    @Column(name = "attendance_percent", precision = 5, scale = 2)
    private BigDecimal attendancePercent;

    @Column(name = "tasks_assigned", nullable = false, columnDefinition = "SMALLINT")
    private Integer tasksAssigned = 0;

    @Column(name = "tasks_completed", nullable = false, columnDefinition = "SMALLINT")
    private Integer tasksCompleted = 0;

    @Column(name = "tasks_overdue_48h", nullable = false, columnDefinition = "SMALLINT")
    private Integer tasksOverdue48h = 0;

    @Column(name = "task_completion_percent", precision = 5, scale = 2)
    private BigDecimal taskCompletionPercent;

    @Column(name = "days_since_last_activity", columnDefinition = "SMALLINT")
    private Integer daysSinceLastActivity;

    @Column(name = "quiz_attempts_count", nullable = false, columnDefinition = "SMALLINT")
    private Integer quizAttemptsCount = 0;

    @Column(name = "quiz_average_percent", precision = 5, scale = 2)
    private BigDecimal quizAveragePercent;

    @Column(name = "consecutive_assignments_missed", nullable = false, columnDefinition = "TINYINT")
    private Integer consecutiveAssignmentsMissed = 0;

    @Column(name = "unsatisfactory_reviews", nullable = false, columnDefinition = "TINYINT")
    private Integer unsatisfactoryReviews = 0;
}
