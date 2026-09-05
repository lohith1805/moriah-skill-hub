package com.moriah.skillhub.sprint.dto;

import com.moriah.skillhub.sprint.entity.TaskStatus;

/** {@code TaskRepository#countTaskStatusesForBatch}'s per-(sprint, status) row — one row per
 * status that actually has at least one task in a given sprint, never a zero-count row (a plain
 * {@code GROUP BY} never produces one). {@code ClientProjectService#progress} (FRS
 * MSH-FR-BA-03/MSH-FR-PM-02) folds this into each sprint's burndown entry so a client sees task
 * counts by status, not just story points — aggregate counts only, never a task title, assignee,
 * or due date, matching {@link SprintProgressProjection}'s own "no individual student data"
 * boundary. */
public record SprintTaskStatusCount(Long sprintId, TaskStatus status, Long count) {
}
