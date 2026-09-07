package com.moriah.skillhub.sprint.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

/** {@code taskId} nullable — build-plan.md feature 11: "the PM defines weekly windows with a due
 * date and an optional linked task." */
public record CreateAssignmentWindowRequest(
        @NotNull Long batchId,
        @NotNull LocalDate weekStart,
        @NotNull LocalDate weekEnd,
        @NotNull Instant dueAt,
        Long taskId
) {
    public CreateAssignmentWindowRequest {
        if (weekStart != null && weekEnd != null && !weekEnd.isAfter(weekStart)) {
            throw new IllegalArgumentException("weekEnd must be after weekStart");
        }
    }
}
