package com.moriah.skillhub.hr.dto;

/**
 * {@code GET /api/v1/hr/onboardings/my-status} — what the caller's own {@code employees} row
 * looks like, for the frontend dashboard gate. {@code staff} is false for a non-employee account
 * (student / client / an admin with no employee record) — those are never gated. {@code
 * accessGated} is true only while a staff member's record is still {@code PENDING_HR} (HR hasn't
 * filled in the real compensation / department / reporting manager and approved it yet).
 */
public record MyOnboardingStatusResponse(
        boolean staff,
        String provisioningStatus,
        boolean accessGated
) {
}
