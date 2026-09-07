package com.moriah.skillhub.assessment.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Unique {@code (quiz_id, user_id, attempt_number)} — same double-POST idempotency shape as
 * {@code TaskSubmission}/{@code Attendance}, guarded by {@code QuizAttemptWriter}. {@code
 * percentage = autoGradedMarks / autoGradableMarks} — a {@code CODE}-only quiz has {@code
 * autoGradableMarks = 0} and {@code percentage} stays {@code null} rather than dividing by zero
 * (build-plan.md feature 14: "CODE answers... excluded from the percentage denominator").
 */
@Entity
@Table(name = "quiz_attempts", uniqueConstraints =
    @UniqueConstraint(columnNames = {"quiz_id", "user_id", "attempt_number"}))
@Getter
@Setter
@NoArgsConstructor
public class QuizAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    // TINYINT UNSIGNED in V8 — see Standup.lateCutoffMinutes' Javadoc.
    @Column(name = "attempt_number", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer attemptNumber;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "auto_graded_marks", precision = 6, scale = 2)
    private BigDecimal autoGradedMarks;

    @Column(name = "auto_gradable_marks", precision = 6, scale = 2)
    private BigDecimal autoGradableMarks;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    private Boolean passed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;
}
