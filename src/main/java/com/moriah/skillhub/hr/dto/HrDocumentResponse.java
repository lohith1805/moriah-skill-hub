package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.HrDocumentStatus;

import java.time.Instant;

public record HrDocumentResponse(
        Long id,
        String userUuid,
        String documentType,
        HrDocumentStatus verificationStatus,
        String verifiedByUuid,
        Instant verifiedAt,
        String rejectionReason
) {
}
