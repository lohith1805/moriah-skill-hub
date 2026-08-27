package com.moriah.skillhub.sprint.dto;

import java.time.Instant;
import java.time.LocalDate;

public record AssignmentWindowResponse(
        Long id,
        Long batchId,
        LocalDate weekStart,
        LocalDate weekEnd,
        Instant dueAt,
        Long taskId
) {
}
