package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.DisciplinaryActionType;
import com.moriah.skillhub.hr.entity.DisciplinarySeverity;
import com.moriah.skillhub.hr.entity.DisciplinaryStatus;

import java.time.Instant;
import java.time.LocalDate;

/** A disciplinary action (gap B1.10). */
public record DisciplinaryActionResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        DisciplinaryActionType actionType,
        DisciplinarySeverity severity,
        LocalDate incidentDate,
        String description,
        String actionTaken,
        DisciplinaryStatus status,
        Instant acknowledgedAt,
        Instant resolvedAt,
        String resolutionNotes,
        Instant createdAt,
        Instant updatedAt
) {
}
