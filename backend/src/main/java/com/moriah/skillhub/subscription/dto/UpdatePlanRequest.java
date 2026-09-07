package com.moriah.skillhub.subscription.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * feature 22: {@code PUT /admin/plans/{id}} — "Plan and pricing configuration at runtime"
 * (build-plan.md). Deliberately excludes {@code code} and {@code tierRank}: {@code code} is the
 * stable identifier every other table's FK/lookup keys off ({@code
 * EntitlementService.resolvePlanId}, {@code batches.plan_tier_min}), and {@code tierRank} is the
 * ordering {@code findByActiveTrueOrderByTierRankAsc}/tier-comparison logic elsewhere assumes is
 * stable — renaming or reordering either is a bigger, riskier change than "update the price and
 * feature flags", which is all build-plan.md's own wording asks for. A full-field replace, not a
 * partial patch, matching this codebase's other {@code Update*Request} records.
 */
public record UpdatePlanRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal priceInr,
        @NotNull @Min(1) Integer durationDays,
        @Min(0) Integer maxProjects,
        boolean mentorSupport,
        boolean allowsBatch,
        boolean allowsSprints,
        boolean allowsPip,
        boolean allowsInternshipLetter,
        boolean allowsClientProject,
        boolean active
) {
}
