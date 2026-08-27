package com.moriah.skillhub.pip.dto;

import com.moriah.skillhub.pip.entity.PipStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/pip/{id}/review} — the day-15 PM decision. {@code outcome} must be one of
 * the three terminal statuses (build-plan.md: "Outcomes CLEARED / TERMINATED / REASSIGNED, all
 * audited"); {@code PipService} rejects {@code TRIGGERED}/{@code IN_PROGRESS} here explicitly,
 * since neither is a review outcome. */
public record ReviewPipRequest(
        @NotNull PipStatus outcome,
        @NotBlank @Size(max = 2000) String reviewNotes
) {
}
