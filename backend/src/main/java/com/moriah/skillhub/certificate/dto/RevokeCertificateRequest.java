package com.moriah.skillhub.certificate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/certificates/{id}/revoke} — a reason is mandatory, mirroring {@code
 * ReviewPipRequest.reviewNotes}'s own "the decision must be explainable" precedent. */
public record RevokeCertificateRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
