package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.EmployeeExitStatus;
import com.moriah.skillhub.hr.entity.ExitType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** An employee offboarding record (gap B1.10). {@code clearanceChecklist} is always a list,
 * never {@code null}. {@code completedAt} is set once the exit is {@code COMPLETED}. */
public record EmployeeExitResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        ExitType exitType,
        LocalDate lastWorkingDay,
        String reason,
        Integer noticePeriodDays,
        EmployeeExitStatus status,
        List<ClearanceItem> clearanceChecklist,
        String exitInterviewNotes,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {

    /** One line of the offboarding clearance checklist (asset return, knowledge transfer, final
     * settlement, …). */
    public record ClearanceItem(String label, boolean done) {
    }
}
