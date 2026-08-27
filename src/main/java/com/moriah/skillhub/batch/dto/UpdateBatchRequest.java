package com.moriah.skillhub.batch.dto;

import com.moriah.skillhub.batch.entity.BatchStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** {@code trackCode} is deliberately not editable here — changing it after students have already
 * been matched against the original track would silently strand them. Everything else about a
 * batch can change. */
public record UpdateBatchRequest(
        @NotBlank @Size(max = 150) String name,
        String planTierMinCode,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Min(1) @Max(500) Integer capacity,
        @NotNull BatchStatus status
) {
    public UpdateBatchRequest {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }
    }
}
