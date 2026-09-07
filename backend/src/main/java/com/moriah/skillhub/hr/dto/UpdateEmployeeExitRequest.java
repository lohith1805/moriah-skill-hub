package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.EmployeeExitStatus;
import com.moriah.skillhub.hr.entity.ExitType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code PUT /api/v1/hr/exits/{id}} (gap B1.10). Full-field replace of the editable fields:
 * exit type, last working day, reason, notice period, working {@code status} (INITIATED ↔
 * IN_PROGRESS ↔ CANCELLED), the clearance checklist and exit-interview notes.
 * <p>
 * {@code status = COMPLETED} is <b>not</b> accepted here — completion has a side effect on the
 * employee record and goes through {@code POST /api/v1/hr/exits/{id}/complete} instead. The
 * compact constructor rejects it.
 */
public record UpdateEmployeeExitRequest(
        @NotNull ExitType exitType,
        @NotNull LocalDate lastWorkingDay,
        @Size(max = 5000) String reason,
        @PositiveOrZero Integer noticePeriodDays,
        @NotNull EmployeeExitStatus status,
        @Size(max = 50) List<@NotNull ClearanceItemInput> clearanceChecklist,
        @Size(max = 20000) String exitInterviewNotes
) {
    public UpdateEmployeeExitRequest {
        if (status == EmployeeExitStatus.COMPLETED) {
            throw new IllegalArgumentException("Use POST /hr/exits/{id}/complete to complete an exit.");
        }
    }

    public record ClearanceItemInput(
            @jakarta.validation.constraints.NotBlank @Size(max = 200) String label,
            boolean done
    ) {
    }
}
