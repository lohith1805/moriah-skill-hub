package com.moriah.skillhub.common.security;

import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Generic ownership comparators — deliberately knows nothing about any specific entity (Batch,
 * TaskSubmission, etc.). Future feature services call {@code requireOwner} with their own
 * resource's owner-id field (e.g. {@code ownershipGuard.requireOwner(currentUserId,
 * batch.getPmId())} for the batch-PM check); {@code OwnershipGuard} never imports those entities.
 * <p>
 * {@code canAccessKey} takes the caller's <b>uuid</b>, not a userId — every currently-defined
 * self-owned storage key ({@code resumes/{userUuid}/...}, {@code hr-documents/{userUuid}/...})
 * embeds the owner's uuid directly (architecture.md "Object Storage"), so those two need no DB
 * lookup at all. {@code projects/{projectId}/assets/...} (feature 15) is different — a project's
 * accessibility depends on its {@code status} (published or not), a DB-backed fact the key alone
 * can't prove — so this one namespace uses a raw {@code JdbcTemplate} read-only projection
 * instead, same "{@code common/} can't import a future feature's entities, but the table already
 * exists" reasoning {@link EntitlementFlagsLoader} already established for {@code
 * user_subscriptions}/{@code subscription_plans}. Every other namespace still resolves with no DB
 * hit. Namespaces owned by a resource id this class doesn't yet recognize ({@code certificates/},
 * {@code invoices/}, {@code submissions/}) deny by default rather than guessing — "signing a key
 * because the caller asked for it is an IDOR" (architecture.md "Object Storage") applies to
 * unrecognized keys too. Two more feature-19 namespaces — {@code payslips/{employeeCode}/...} and
 * {@code hr-letters/{userUuid}/...} (the latter not in architecture.md's Object Storage list at
 * all, since no {@code hr_letters} table exists to hang a documented key template off — see
 * {@code HrLetterService}'s Javadoc) — both need "is this an HR_MANAGER/ADMIN, OR the resource's
 * own owner" rather than owner-only, since HR staff routinely act on someone else's payslip or
 * letter; both resolve with a raw {@code JdbcTemplate} read, same reasoning as {@link
 * #canAccessProject}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OwnershipGuard {

    private final JdbcTemplate jdbcTemplate;

    public void requireOwner(Long currentUserId, Long resourceOwnerId) {
        if (!Objects.equals(currentUserId, resourceOwnerId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }

    public boolean canAccessKey(String callerUuid, String key) {
        String[] segments = key.split("/", 3);
        boolean allowed;
        if (key.startsWith("resumes/") || key.startsWith("hr-documents/")) {
            allowed = segments.length >= 2 && segments[1].equals(callerUuid);
        } else if (key.startsWith("projects/")) {
            allowed = segments.length >= 2 && canAccessProject(segments[1], callerUuid);
        } else if (key.startsWith("payslips/")) {
            allowed = segments.length >= 2 && canAccessPayslip(segments[1], callerUuid);
        } else if (key.startsWith("hr-letters/")) {
            allowed = segments.length >= 2 && canAccessHrLetter(segments[1], callerUuid);
        } else {
            allowed = false;
        }

        if (!allowed) {
            log.warn("[security/ownership] denied presign for unrecognized or non-owned key: key={} callerUuid={}", key, callerUuid);
        }
        return allowed;
    }

    /** A published project's assets are visible to anyone who can browse the portal; a {@code
     * DRAFT}/{@code ARCHIVED} one is visible only to its creator or an {@code ADMIN} — matches
     * {@code ProjectService#requireCreatorOrAdmin}'s own mutation-side authorization exactly, so
     * whoever is allowed to edit a draft project is also the one who can see its asset URLs. */
    private boolean canAccessProject(String projectIdSegment, String callerUuid) {
        long projectId;
        try {
            projectId = Long.parseLong(projectIdSegment);
        } catch (NumberFormatException e) {
            return false;
        }
        // "accessible" is a MySQL reserved word (partitioning grammar) — "is_accessible" avoids
        // the resulting syntax error (confirmed the hard way).
        Boolean accessible = jdbcTemplate.query("""
                SELECT (
                    p.status = 'PUBLISHED'
                    OR creator.uuid = ?
                    OR EXISTS (
                        SELECT 1 FROM users caller
                          JOIN user_roles ur ON ur.user_id = caller.id
                          JOIN roles r ON r.id = ur.role_id
                         WHERE caller.uuid = ? AND r.code = 'ADMIN'
                    )
                ) AS is_accessible
                  FROM projects p JOIN users creator ON creator.id = p.created_by
                 WHERE p.id = ?
                """,
                (rs, rowNum) -> rs.getBoolean("is_accessible"),
                callerUuid, callerUuid, projectId)
                .stream().findFirst().orElse(false);
        return Boolean.TRUE.equals(accessible);
    }

    /** feature 19: HR_MANAGER/ADMIN (the only callers {@code GET /hr/payroll?month=} allows in
     * the first place) may sign any payslip; an employee whose own {@code employee_code} matches
     * the key's segment may sign their own — future-proofing for a self-service "my payslip"
     * endpoint that doesn't exist yet, cheap to include since the query is already keyed on the
     * same segment. */
    private boolean canAccessPayslip(String employeeCodeSegment, String callerUuid) {
        Boolean ownsPayslip = jdbcTemplate.query("""
                SELECT EXISTS (
                    SELECT 1 FROM employees e JOIN users owner ON owner.id = e.user_id
                     WHERE e.employee_code = ? AND owner.uuid = ?
                ) AS is_owner
                """,
                (rs, rowNum) -> rs.getBoolean("is_owner"),
                employeeCodeSegment, callerUuid)
                .stream().findFirst().orElse(false);
        return Boolean.TRUE.equals(ownsPayslip) || isHrStaff(callerUuid);
    }

    /** {@code hr-letters/{userUuid}/...} — the target's own uuid is embedded in the key, but the
     * typical caller is the HR_MANAGER who generated it on the target's behalf, not the target
     * themselves; a plain segment-equality check (like {@code resumes/}) would only ever let the
     * target download their own letter, never the HR staff who actually calls {@code POST
     * /hr/letters/{type}}. */
    private boolean canAccessHrLetter(String userUuidSegment, String callerUuid) {
        return userUuidSegment.equals(callerUuid) || isHrStaff(callerUuid);
    }

    /** Shared "is this caller HR_MANAGER or ADMIN" check backing both {@link #canAccessPayslip}
     * and {@link #canAccessHrLetter} — kept in one place so a future role rename/addition only
     * needs to change here, not in two independently-drifting copies. */
    private boolean isHrStaff(String callerUuid) {
        Boolean isHr = jdbcTemplate.query("""
                SELECT EXISTS (
                    SELECT 1 FROM users caller
                      JOIN user_roles ur ON ur.user_id = caller.id
                      JOIN roles r ON r.id = ur.role_id
                     WHERE caller.uuid = ? AND r.code IN ('HR_MANAGER', 'ADMIN')
                ) AS is_hr
                """,
                (rs, rowNum) -> rs.getBoolean("is_hr"),
                callerUuid)
                .stream().findFirst().orElse(false);
        return Boolean.TRUE.equals(isHr);
    }

    public void requireKeyAccess(String callerUuid, String key) {
        if (!canAccessKey(callerUuid, key)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }
}
