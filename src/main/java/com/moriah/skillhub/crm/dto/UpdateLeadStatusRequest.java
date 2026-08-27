package com.moriah.skillhub.crm.dto;

import com.moriah.skillhub.crm.entity.LeadStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code reason} is required for any backward pipeline move and is stored on the automatic
 * {@code STATUS_CHANGE} activity — build-plan.md: "Backward transitions allowed with a logged
 * reason." {@code lostReason} is required only when {@code newStatus == LOST}; {@code
 * convertedUserUuid} only when {@code newStatus == ENROLLED} — {@code uuid}, not the internal
 * {@code users.id}, per architecture.md's "no endpoint exposes users.id" invariant. Forward-vs-
 * backward is determined server-side from the lead's current status, so this DTO can't validate
 * that part itself.
 */
public record UpdateLeadStatusRequest(
        @NotNull LeadStatus newStatus,
        @Size(max = 500) String reason,
        @Size(max = 500) String lostReason,
        String convertedUserUuid
) {
    public UpdateLeadStatusRequest {
        if (newStatus == LeadStatus.LOST && (lostReason == null || lostReason.isBlank())) {
            throw new IllegalArgumentException("lostReason is required when newStatus is LOST");
        }
        if (newStatus == LeadStatus.ENROLLED && (convertedUserUuid == null || convertedUserUuid.isBlank())) {
            throw new IllegalArgumentException("convertedUserUuid is required when newStatus is ENROLLED");
        }
    }
}
