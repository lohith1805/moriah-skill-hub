package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.OnboardingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code PUT /api/v1/hr/onboardings/{id}} (gap B1.10). Full-field replace of the editable
 * fields. {@code status = COMPLETED} is accepted here (unlike an exit, completion has no side
 * effect) and stamps {@code completedAt} the first time it is set.
 */
public record UpdateOnboardingRequest(
        @NotNull LocalDate startDate,
        String buddyUuid,
        @NotNull OnboardingStatus status,
        @Size(max = 50) List<@NotNull ChecklistItemInput> checklist,
        @Size(max = 20000) String notes
) {

    public record ChecklistItemInput(
            @NotBlank @Size(max = 200) String label,
            boolean done
    ) {
    }
}
