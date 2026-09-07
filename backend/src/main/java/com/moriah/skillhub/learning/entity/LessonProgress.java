package com.moriah.skillhub.learning.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One user's progress through one lesson (gap B1.4). A row is created the first time the user
 * reports progress; {@code (lesson_id, user_id)} is unique so a double-POST updates in place
 * rather than inserting twice. {@code lessonId}/{@code userId} are bare ids — this is a join-row,
 * not an aggregate that navigates.
 */
@Entity
@Table(name = "lesson_progress",
        uniqueConstraints = @UniqueConstraint(name = "uq_lesson_progress", columnNames = {"lesson_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class LessonProgress extends BaseEntity {

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LessonProgressStatus status = LessonProgressStatus.IN_PROGRESS;

    @Column(name = "watched_seconds", nullable = false, columnDefinition = "INT UNSIGNED")
    private int watchedSeconds;

    @Column(name = "completed_at")
    private Instant completedAt;
}
