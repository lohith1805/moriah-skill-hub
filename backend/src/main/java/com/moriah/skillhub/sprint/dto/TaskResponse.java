package com.moriah.skillhub.sprint.dto;

import com.moriah.skillhub.sprint.entity.TaskStatus;
import com.moriah.skillhub.sprint.entity.TaskType;

import java.time.Instant;

/** {@code assignedToUuid}/{@code assignedToName}, never {@code assignedTo}'s internal id — same
 * convention as {@code BatchResponse.pmUuid}/{@code pmFullName}. Both null when the task is still
 * {@code BACKLOG}. */
public record TaskResponse(
        Long id,
        Long sprintId,
        Long projectId,
        String title,
        String description,
        TaskType taskType,
        String assignedToUuid,
        String assignedToName,
        Integer storyPoints,
        Instant dueAt,
        TaskStatus status,
        Instant completedAt
) {
}
