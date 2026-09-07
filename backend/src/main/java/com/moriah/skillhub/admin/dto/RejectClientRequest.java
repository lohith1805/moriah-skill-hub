package com.moriah.skillhub.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/admin/client-requests/{uuid}/reject} — the reason is recorded in the audit
 * log and included in the "not approved" email to the applicant. */
public record RejectClientRequest(@NotBlank @Size(max = 500) String reason) {
}
