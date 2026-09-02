package com.moriah.skillhub.subscription.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * {@code POST /api/v1/admin/plans} (gap B1.13). Adds the two fields {@code UpdatePlanRequest}
 * deliberately withholds — {@code code} and {@code tierRank} — because at creation time they
 * have to be set once; afterwards they are frozen (see {@code UpdatePlanRequest}'s Javadoc for
 * why renaming/reordering an existing plan is out of scope). {@code code} matches the
 * {@code UPPER_SNAKE} convention of the five seeded plans and must be unique.
 */
public record CreatePlanRequest(
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
                message = "code must be upper-case letters, digits and underscore") String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal priceInr,
        @NotNull @Min(1) Integer tierRank,
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
