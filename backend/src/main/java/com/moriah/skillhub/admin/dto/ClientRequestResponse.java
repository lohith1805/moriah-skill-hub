package com.moriah.skillhub.admin.dto;

import com.moriah.skillhub.user.entity.UserStatus;

import java.time.Instant;

/**
 * One row of the client-registration review queue ({@code GET /api/v1/admin/client-requests}) and
 * the body returned by approve / reject. {@code uuid} is the applicant's user uuid — the path
 * variable for the approve / reject calls. {@code status} is {@code PENDING_APPROVAL},
 * {@code ACTIVE} (just approved), or {@code REJECTED}.
 */
public record ClientRequestResponse(
        String uuid,
        String fullName,
        String email,
        String phone,
        String companyName,
        String industry,
        UserStatus status,
        Instant submittedAt
) {
}
