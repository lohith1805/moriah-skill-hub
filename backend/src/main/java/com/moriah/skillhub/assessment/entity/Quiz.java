package com.moriah.skillhub.assessment.entity;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.common.util.Constants;
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

/**
 * {@code batch} is a real {@code @ManyToOne Batch}, nullable — the established shared-kernel
 * exception ({@code Sprint}'s Javadoc), but unlike {@code Sprint}/{@code Standup} a quiz can be
 * project-scoped instead of batch-scoped (V8: {@code batch_id NULL}). {@code projectId} stays a
 * bare {@code Long} — {@code project/} has no entity class yet (feature 15), same treatment as
 * {@code Task.projectId}.
 */
@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
public class Quiz extends BaseEntity {

    @Column(name = "project_id")
    private Long projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(nullable = false, length = 200)
    private String title;

    // SMALLINT UNSIGNED in V8 — see Standup.lateCutoffMinutes' Javadoc for why this needs an
    // explicit columnDefinition override.
    @Column(name = "duration_minutes", nullable = false, columnDefinition = "SMALLINT UNSIGNED")
    private Integer durationMinutes;

    @Column(name = "pass_percentage", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer passPercentage = Constants.QUIZ_PASS_PERCENTAGE;

    @Column(name = "max_attempts", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer maxAttempts = Constants.QUIZ_DEFAULT_MAX_ATTEMPTS;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
