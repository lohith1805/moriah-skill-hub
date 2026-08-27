package com.moriah.skillhub.certificate.dto;

import com.moriah.skillhub.certificate.entity.CertificateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code POST /api/v1/certificates/issue}. {@code certificateType} is nullable — {@code
 * CertificateService#issue} defaults a missing value to {@code COMPLETION} (build-plan.md feature
 * 20 decision), so no {@code @NotNull} here. {@code userUuid}, never a raw {@code users.id} —
 * matches every other request boundary in this project (e.g. {@code AddStudentRequest}). */
public record IssueCertificateRequest(
        @NotNull Long batchId,
        @NotBlank String userUuid,
        CertificateType certificateType
) {
}
