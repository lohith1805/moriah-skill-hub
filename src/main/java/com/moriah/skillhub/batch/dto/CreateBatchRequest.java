package com.moriah.skillhub.batch.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** build-plan.md feature 10: "Creator becomes pm_id" — no {@code pmId} field here; {@code
 * BatchService} sets it from {@code @CurrentUser}. {@code planTierMinCode} is a plan code
 * (matching {@code CheckoutRequest.planCode}'s own convention — never a raw {@code
 * subscription_plans.id}), null meaning no minimum tier. */
public record CreateBatchRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 30) String trackCode,
        String planTierMinCode,
        @NotNull @FutureOrPresent LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Min(1) @Max(500) Integer capacity
) {
    public CreateBatchRequest {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }
    }
}
