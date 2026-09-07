package com.moriah.skillhub.learning.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One learner's latest quiz attempt for a lesson (gap B1.4). Unique on {@code (lesson_id,
 * user_id)} — a re-submit updates in place. */
@Entity
@Table(name = "lesson_quiz_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uq_lesson_quiz_attempts", columnNames = {"lesson_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class LessonQuizAttempt extends BaseEntity {

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer score;

    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer total;

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
