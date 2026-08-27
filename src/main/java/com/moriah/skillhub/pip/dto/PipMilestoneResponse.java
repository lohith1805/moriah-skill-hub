package com.moriah.skillhub.pip.dto;

import com.moriah.skillhub.pip.entity.PipMilestoneStatus;

import java.time.Instant;
import java.time.LocalDate;

public record PipMilestoneResponse(
        Long id,
        String title,
        String description,
        LocalDate dueDate,
        PipMilestoneStatus status,
        Instant completedAt,
        String verifiedByUuid
) {
}
