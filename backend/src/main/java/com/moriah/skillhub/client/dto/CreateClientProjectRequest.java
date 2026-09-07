package com.moriah.skillhub.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/clients/projects} — CLIENT-only; the caller's own {@code client_id} is
 * resolved server-side from {@code clients.user_id}, never accepted from the request body (the
 * same "never accept the caller's own identity from the body" rule {@code CurrentUser}'s Javadoc
 * establishes generally). {@code additionalNotes} is optional free-text context beyond the
 * one-line {@code scopeDescription}. */
public record CreateClientProjectRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String scopeDescription,
        @Size(max = 50) String budgetRange,
        @Size(max = 5000) String additionalNotes
) {
}
