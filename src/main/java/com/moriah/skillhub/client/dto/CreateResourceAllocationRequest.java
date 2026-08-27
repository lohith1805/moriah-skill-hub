package com.moriah.skillhub.client.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** {@code POST /api/v1/ba/allocations} — BUSINESS_ANALYST/ADMIN only. {@code userUuid}, never a
 * raw {@code users.id} (the same request-boundary rule every other feature's create endpoint
 * already follows, e.g. {@code IssueCertificateRequest.userUuid}). */
public record CreateResourceAllocationRequest(
        @NotNull Long clientProjectId,
        @NotNull Long batchId,
        @NotBlank String userUuid,
        @NotBlank @Size(max = 100) String roleInProject,
        @NotNull @Min(1) Integer allocatedDays,
        @Min(0) Integer storyPointsEstimate,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate
) {
    public CreateResourceAllocationRequest {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate must not be before fromDate");
        }
    }
}
