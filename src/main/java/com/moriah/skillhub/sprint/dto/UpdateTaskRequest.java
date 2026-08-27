package com.moriah.skillhub.sprint.dto;

import com.moriah.skillhub.sprint.entity.TaskStatus;
import com.moriah.skillhub.sprint.entity.TaskType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** {@code sprintId} is deliberately not editable here (one of the open questions build-plan.md
 * leaves for this feature to decide) — same "identifying FK is immutable after creation"
 * reasoning as {@code UpdateSprintRequest}'s {@code batchId}. Moving a task to a different
 * sprint would silently invalidate whatever {@code completed_points} rollup already happened
 * against its original sprint. {@code status} drives the state machine forward
 * ({@code TaskService.ALLOWED_TRANSITIONS}) — {@code BACKLOG -> ASSIGNED} is explicitly rejected
 * here even though the transition table allows it in principle; that specific move only happens
 * through {@code POST /tasks/{id}/assign} or {@code /pull}, which carry eligibility checks this
 * generic endpoint doesn't (batch membership, the PIP pull-block hook). */
public record UpdateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull TaskType taskType,
        @Min(0) @Max(100) Integer storyPoints,
        Instant dueAt,
        @NotNull TaskStatus status
) {
}
