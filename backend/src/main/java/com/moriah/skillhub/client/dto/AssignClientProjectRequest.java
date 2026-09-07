package com.moriah.skillhub.client.dto;

/**
 * {@code PUT /api/v1/admin/client-projects/{id}/assignment} — ADMIN manual override of the
 * round-robin assignment. Either field may be {@code null} to leave that assignment untouched;
 * at least one must be present (enforced in {@code ClientProjectService#assign}).
 */
public record AssignClientProjectRequest(
        String baUuid,
        String developerUuid
) {
}
