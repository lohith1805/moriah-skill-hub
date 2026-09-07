package com.moriah.skillhub.subscription.dto;

import java.math.BigDecimal;

/**
 * Admin view of a {@code subscription_plans} row (gap B1.13). Unlike the public {@link
 * PlanResponse} this carries the numeric {@code id} — the identifier {@code PUT}/{@code DELETE
 * /api/v1/admin/plans/{id}} key off — plus {@code maxProjects} and the {@code active} flag, and
 * the listing includes deactivated plans so an admin can still see (and potentially reactivate)
 * them. {@code subscription_plans} has no {@code uuid} column (architecture.md V3), so the
 * numeric id is the identifier here, same reasoning {@code AdminPlanController} already gives.
 */
public record AdminPlanResponse(
        Long id,
        String code,
        String name,
        BigDecimal priceInr,
        Integer tierRank,
        Integer durationDays,
        Integer maxProjects,
        boolean mentorSupport,
        boolean allowsBatch,
        boolean allowsSprints,
        boolean allowsPip,
        boolean allowsInternshipLetter,
        boolean allowsClientProject,
        boolean active
) {
}
