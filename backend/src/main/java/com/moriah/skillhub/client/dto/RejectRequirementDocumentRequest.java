package com.moriah.skillhub.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/requirement-documents/{id}/reject} and {@code PUT /api/v1/ba/documents/
 * {id}/reject}. {@code reason} is required — a reject with no feedback tells the author nothing
 * they can act on, unlike an approve, which speaks for itself. */
public record RejectRequirementDocumentRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
