package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code reason} is required for any backward pipeline move and is stored on the automatic
 * {@code STATUS_CHANGE} activity — build-plan.md: "Backward transitions allowed with a logged
 * reason." {@code lostReason} is required only when {@code newStatus == LOST}.
 * <p>
 * For {@code newStatus == ENROLLED} the converted student must be identified — by {@code
 * convertedUserUuid} ({@code uuid}, never the internal {@code users.id}) or, since a lead-gen
 * agent naturally holds the student's email and has no way to resolve it to a uuid, by {@code
 * convertedUserEmail}. Exactly one is needed; {@code uuid} wins if both are given.
 * Forward-vs-backward is determined server-side from the lead's current status.
 */
public record UpdateLeadStatusRequest(
        @NotNull LeadStatus newStatus,
        @Size(max = 500) String reason,
        @Size(max = 500) String lostReason,
        String convertedUserUuid,
        @Size(max = 255) String convertedUserEmail
) {
    public UpdateLeadStatusRequest {
        if (newStatus == LeadStatus.LOST && (lostReason == null || lostReason.isBlank())) {
            throw new IllegalArgumentException("lostReason is required when newStatus is LOST");
        }
        if (newStatus == LeadStatus.ENROLLED
                && (convertedUserUuid == null || convertedUserUuid.isBlank())
                && (convertedUserEmail == null || convertedUserEmail.isBlank())) {
            throw new IllegalArgumentException(
                    "convertedUserUuid or convertedUserEmail is required when newStatus is ENROLLED");
        }
    }
}
