package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.ExitType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** {@code POST /api/v1/hr/exits} (gap B1.10) — start an employee's offboarding. Lands
 * {@code INITIATED}. The clearance checklist is built up afterwards via {@code PUT}. */
public record InitiateEmployeeExitRequest(
        @NotNull Long employeeId,
        @NotNull ExitType exitType,
        @NotNull LocalDate lastWorkingDay,
        @Size(max = 5000) String reason,
        @PositiveOrZero Integer noticePeriodDays
) {
}
