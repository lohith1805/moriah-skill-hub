package com.moriah.skillhub.sprint.dto;

import com.moriah.skillhub.sprint.entity.SprintStatus;

import java.time.LocalDate;

public record SprintResponse(
        Long id,
        Long batchId,
        Integer sprintNumber,
        String goal,
        LocalDate startDate,
        LocalDate endDate,
        SprintStatus status,
        Integer plannedPoints,
        Integer completedPoints
) {
}
