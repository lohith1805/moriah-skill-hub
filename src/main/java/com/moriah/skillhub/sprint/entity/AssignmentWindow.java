package com.moriah.skillhub.sprint.entity;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Lives in {@code sprint/}, not {@code assessment/} — a deliberate deviation from
 * architecture.md's package diagram, which lists {@code AssignmentWindow} under the
 * not-yet-built {@code assessment/} package (feature 14). build-plan.md's own feature 11 section
 * is explicit and singular: "{@code assignment_windows} created here" — with
 * {@code POST /api/v1/assignment-windows} in feature 11's endpoint list — and feature 14's own
 * endpoint list (build-plan.md) has no assignment-window endpoints at all, so there is no future
 * collision to avoid. Table already exists (V8, applied at feature 06); no migration needed here.
 * Documented in progress-tracker.md's 2026-08-25 decision log. {@code batch} follows the same
 * shared-kernel treatment as {@code Sprint.batch}; {@code task} is a same-package association.
 */
@Entity
@Table(name = "assignment_windows", uniqueConstraints =
    @UniqueConstraint(columnNames = {"batch_id", "week_start"}))
@Getter
@Setter
@NoArgsConstructor
public class AssignmentWindow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    private LocalDate weekEnd;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;
}
