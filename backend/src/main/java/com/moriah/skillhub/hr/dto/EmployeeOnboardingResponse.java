package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.OnboardingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** An employee onboarding record (gap B1.10). {@code checklist} is always a list, never null. */
public record EmployeeOnboardingResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        String employeeUserUuid,
        Long buddyId,
        LocalDate startDate,
        OnboardingStatus status,
        List<ChecklistItem> checklist,
        String notes,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public record ChecklistItem(String label, boolean done) {
    }
}
