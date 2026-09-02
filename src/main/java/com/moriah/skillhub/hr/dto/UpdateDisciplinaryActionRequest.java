package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.DisciplinaryActionType;
import com.moriah.skillhub.hr.entity.DisciplinarySeverity;
import com.moriah.skillhub.hr.entity.DisciplinaryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * {@code PUT /api/v1/hr/disciplinary/{id}} (gap B1.10). Full-field replace. Advancing
 * {@code status} to {@code ACKNOWLEDGED} stamps {@code acknowledgedAt}; to {@code RESOLVED}
 * stamps {@code resolvedAt} — each once.
 */
public record UpdateDisciplinaryActionRequest(
        @NotNull DisciplinaryActionType actionType,
        @NotNull DisciplinarySeverity severity,
        @NotNull LocalDate incidentDate,
        @NotBlank @Size(max = 20000) String description,
        @Size(max = 20000) String actionTaken,
        @NotNull DisciplinaryStatus status,
        @Size(max = 20000) String resolutionNotes
) {
}
