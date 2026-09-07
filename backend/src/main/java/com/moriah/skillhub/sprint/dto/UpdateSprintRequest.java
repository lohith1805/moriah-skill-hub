package com.moriah.skillhub.sprint.dto;

import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.sprint.entity.SprintStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** {@code batchId}/{@code sprintNumber} are deliberately not editable here — same reasoning as
 * {@code UpdateBatchRequest}'s immutable {@code trackCode}: moving a sprint to a different batch
 * or renumbering it after tasks may already reference it would silently strand history. {@code
 * status} is present (unlike a narrower design that routes every status change through {@code
 * POST /sprints/{id}/activate} alone) because build-plan.md's endpoint list has no dedicated
 * "complete a sprint" action — {@code SprintService.update} and {@code .activate} both funnel
 * through the same {@code transitionStatus} state-machine check, so a {@code PLANNED -> ACTIVE}
 * request made via this endpoint is held to the identical "no other ACTIVE sprint" / "previous
 * sprint COMPLETED" invariants as the dedicated endpoint, not a looser one. Same precedent as
 * {@code UpdateBatchRequest.status} being a plain settable field. */
public record UpdateSprintRequest(
        @NotBlank @Size(max = 500) String goal,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Min(0) @Max(200) Integer plannedPoints,
        @NotNull SprintStatus status
) {
    public UpdateSprintRequest {
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
