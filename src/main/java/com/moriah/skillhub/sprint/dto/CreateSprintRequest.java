package com.moriah.skillhub.sprint.dto;

import com.moriah.skillhub.common.util.Constants;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** code-standards.md's own canonical {@code CreateSprintRequest} example, verbatim on every
 * field, plus the 1-2-week duration check the example itself doesn't show but build-plan.md
 * feature 11 requires ("Sprints run 1-2 weeks"). */
public record CreateSprintRequest(
        @NotNull Long batchId,
        @NotNull @Min(1) @Max(52) Integer sprintNumber,
        @NotBlank @Size(max = 500) String goal,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull LocalDate endDate,
        @Min(0) @Max(200) Integer plannedPoints
) {
    public CreateSprintRequest {
        if (startDate != null && endDate != null) {
            if (!endDate.isAfter(startDate)) {
                throw new IllegalArgumentException("endDate must be after startDate");
            }
            long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            if (days < Constants.SPRINT_MIN_DURATION_DAYS || days > Constants.SPRINT_MAX_DURATION_DAYS) {
                throw new IllegalArgumentException("A sprint must run 1-2 weeks (7-14 days inclusive)");
            }
        }
    }
}
