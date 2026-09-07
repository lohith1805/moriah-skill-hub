package com.moriah.skillhub.hr.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/hr/attendance/checkin}. {@code userUuid} is optional: omitted (or
 * the caller's own) = a self check-in; an HR_MANAGER/ADMIN may pass another staff member's uuid
 * to log a biometric-terminal check-in on their behalf. {@code device} is a free-text terminal /
 * channel id (BIO-GATE-01, WEB-AUTH-PORTAL, …).
 */
public record StaffCheckinRequest(
        @Size(max = 36) String userUuid,
        @Size(max = 50) String device
) {
}
