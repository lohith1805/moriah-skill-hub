package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.DisciplinaryActionType;
import com.moriah.skillhub.hr.entity.DisciplinarySeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** {@code POST /api/v1/hr/disciplinary} (gap B1.10). Lands {@code OPEN}. */
public record CreateDisciplinaryActionRequest(
        @NotNull Long employeeId,
        @NotNull DisciplinaryActionType actionType,
        @NotNull DisciplinarySeverity severity,
        @NotNull LocalDate incidentDate,
        @NotBlank @Size(max = 20000) String description
) {
}
