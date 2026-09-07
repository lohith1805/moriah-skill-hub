package com.moriah.skillhub.client.dto;

import com.moriah.skillhub.client.entity.RequirementDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/ba/documents} — BUSINESS_ANALYST/ADMIN only. Always lands {@code
 * IN_REVIEW} (see {@code RequirementDocumentStatus}'s own Javadoc); there is deliberately no
 * {@code status} field here — this endpoint doesn't accept one. */
public record CreateRequirementDocumentRequest(
        @NotNull Long clientProjectId,
        @NotNull RequirementDocumentType docType,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content
) {
}
