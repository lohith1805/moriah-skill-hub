package com.moriah.skillhub.hr.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** {@code POST /api/v1/hr/onboardings} (gap B1.10). Starts {@code NOT_STARTED}. {@code buddyUuid}
 * is an optional assigned mentor. The checklist is built up via {@code PUT}. */
public record CreateOnboardingRequest(
        @NotNull Long employeeId,
        @NotNull LocalDate startDate,
        String buddyUuid
) {
}
