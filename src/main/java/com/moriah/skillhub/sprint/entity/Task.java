package com.moriah.skillhub.sprint.entity;

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

import java.time.Instant;

/**
 * {@code sprint} is a same-package association (no cross-package question). {@code assignedTo} is
 * a real {@code @ManyToOne User} — the established shared-kernel exception. {@code projectId} is
 * a bare {@code Long}, not {@code @ManyToOne Project} — unlike {@code batch}/{@code sprint}'s
 * canonical treatment, {@code project/} has no entity class yet (it isn't built until a later
 * feature per architecture.md's package diagram); same bare-id cross-package pattern as
 * {@code Payment.planId}. The V6 migration's FK to {@code projects} still exists at the DB layer
 * (added at the end of V8) — only the Java-side association is deferred.
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
public class Task extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;

    @Column(name = "project_id")
    private Long projectId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    private TaskType taskType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    // TINYINT UNSIGNED in V6 — see Sprint.sprintNumber's Javadoc for why this needs an explicit
    // columnDefinition override.
    @Column(name = "story_points", columnDefinition = "TINYINT UNSIGNED")
    private Integer storyPoints;

    @Column(name = "due_at")
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.BACKLOG;

    @Column(name = "completed_at")
    private Instant completedAt;
}
