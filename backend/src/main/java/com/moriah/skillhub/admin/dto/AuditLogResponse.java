package com.moriah.skillhub.admin.dto;

import java.time.Instant;

/**
 * {@code userUuid} is the <b>actor</b> — {@code audit_logs.user_id} always records who performed
 * the action, not who it was performed on (confirmed against every {@code
 * AuditLogService.record(callerUserId, ...)} call site in this codebase — e.g. {@code
 * GraduationService}/{@code HrLetterService}/{@code ClientService} all pass the staff member's id
 * first and the affected person's id as {@code entityId}). Resolved via a join in {@code
 * AuditQueryService}'s own SQL, never the internal id (architecture.md: "No endpoint exposes
 * users.id"). Nullable — {@code audit_logs.user_id} itself is nullable for system-initiated
 * actions (architecture.md V2).
 * <p>
 * {@code entityId}/{@code entityUuid}: the invariant above applies just as much to the *entity*
 * side whenever {@code entityType} happens to be {@code "User"} (a suspension, role change,
 * graduation, letter issuance, etc. all record the affected user's raw {@code id} in {@code
 * entity_id} — the same column every non-user entity type, e.g. {@code "Sprint"}/{@code
 * "Certificate"}, uses for its own, unrestricted numeric id). {@code AuditQueryService} resolves
 * that one case to {@code entityUuid} and leaves {@code entityId} {@code null}; every other entity
 * type gets the reverse (a plain {@code entityId}, {@code entityUuid} left {@code null}) — {@code
 * "User"} is the only entity type this invariant restricts, so a single conditional resolves it
 * without a per-entity-type special-case matrix.
 * <p>
 * {@code oldValue}/{@code newValue} are returned as raw JSON text, not parsed — this is a
 * read-only diagnostic view, not a typed API contract over what every past and future audited
 * entity happens to look like.
 */
public record AuditLogResponse(
        Long id,
        String userUuid,
        String action,
        String entityType,
        Long entityId,
        String entityUuid,
        String oldValue,
        String newValue,
        String ipAddress,
        Instant createdAt
) {
}
