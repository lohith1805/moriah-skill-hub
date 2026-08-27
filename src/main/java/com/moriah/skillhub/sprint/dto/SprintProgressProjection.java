package com.moriah.skillhub.sprint.dto;

import com.moriah.skillhub.sprint.entity.SprintStatus;

/** feature 21 (BA and Client Portal): {@code ClientProjectService#progress}'s per-sprint input —
 * every sprint for a batch (unlike {@link VelocityProjection}, which is {@code COMPLETED}-only),
 * reusing the same {@code plannedPoints}/{@code completedPoints} rollup {@code
 * TaskService#completeTask} already maintains per sprint rather than a second, independent
 * aggregate query over {@code tasks}. */
public record SprintProgressProjection(
        Long sprintId,
        Integer sprintNumber,
        SprintStatus status,
        Integer plannedPoints,
        Integer completedPoints
) {
}
