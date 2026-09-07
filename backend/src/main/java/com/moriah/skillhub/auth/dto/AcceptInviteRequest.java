package com.moriah.skillhub.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/auth/accept-invite} — the raw token from the emailed link plus the
 * password the invitee chooses. On success the account flips {@code INVITED -> ACTIVE} and a
 * token pair is issued (or a 2FA challenge, for an invited ADMIN/HR_MANAGER). */
public record AcceptInviteRequest(
        @NotBlank String token,
        // BCrypt truncates input silently beyond 72 bytes — same cap as RegisterRequest.
        @NotBlank @Size(min = 8, max = 64) String password
) {
}
