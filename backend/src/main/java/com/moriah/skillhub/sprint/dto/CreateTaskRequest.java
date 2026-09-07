package com.moriah.skillhub.sprint.dto;

import com.moriah.skillhub.sprint.entity.TaskType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** No {@code assignedTo} field — a task is always created {@code BACKLOG} (build-plan.md feature
 * 11's state machine starts there); assignment happens afterward via {@code POST
 * /tasks/{id}/assign} (PM-driven) or {@code /pull} (student self-service), each with its own
 * eligibility checks that creation shouldn't bypass. */
public record CreateTaskRequest(
        @NotNull Long sprintId,
        Long projectId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull TaskType taskType,
        @Min(0) @Max(100) Integer storyPoints,
        Instant dueAt
) {
}
