package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.HrDocumentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VerifyHrDocumentRequest(
        @NotNull HrDocumentStatus decision,
        @Size(max = 500) String rejectionReason
) {
    public VerifyHrDocumentRequest {
        if (decision == HrDocumentStatus.PENDING) {
            throw new IllegalArgumentException("decision must be VERIFIED or REJECTED");
        }
        if (decision == HrDocumentStatus.REJECTED && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new IllegalArgumentException("rejectionReason is required when decision is REJECTED");
        }
    }
}
