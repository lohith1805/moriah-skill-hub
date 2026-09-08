package com.moriah.skillhub.permission;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.permission.dto.PermissionMatrixResponse;
import com.moriah.skillhub.permission.entity.Permission;
import com.moriah.skillhub.permission.repository.PermissionRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The granular-permission framework. Catalogue rows live in {@code permissions} (V1 schema, V52
 * seed); the {@code role_permissions} join (also V1) is read/written here with {@link
 * JdbcTemplate} — it's a pure FK join table with no behaviour, so an entity would only add
 * ceremony (same call the {@code OwnershipGuard} already makes for role lookups).
 * <p>
 * Nothing calls {@link #codesForRoles} in a security check yet — the wiring exists so a future
 * endpoint can gate on {@code @PreAuthorize("@perms.has('CODE')")} instead of a coarse {@code
 * hasRole(...)}.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PermissionMatrixResponse matrix() {
        List<PermissionMatrixResponse.PermissionEntry> catalogue = permissionRepository
                .findAllByOrderByModuleAscCodeAsc().stream()
                .map(p -> new PermissionMatrixResponse.PermissionEntry(p.getCode(), p.getDescription(), p.getModule()))
                .toList();

        Map<String, List<String>> byRole = new LinkedHashMap<>();
        for (RoleCode rc : RoleCode.values()) {
            byRole.put(rc.name(), new ArrayList<>());
        }
        jdbcTemplate.query("""
                SELECT r.code AS role_code, p.code AS perm_code
                  FROM role_permissions rp
                  JOIN roles r ON r.id = rp.role_id
                  JOIN permissions p ON p.id = rp.permission_id
                """, rs -> {
            List<String> list = byRole.get(rs.getString("role_code"));
            if (list != null) {
                list.add(rs.getString("perm_code"));
            }
        });
        byRole.values().forEach(java.util.Collections::sort);

        List<PermissionMatrixResponse.RoleGrants> roles = byRole.entrySet().stream()
                .map(e -> new PermissionMatrixResponse.RoleGrants(e.getKey(), e.getValue()))
                .toList();

        return new PermissionMatrixResponse(catalogue, roles);
    }

    @Transactional
    public PermissionMatrixResponse setRolePermissions(RoleCode roleCode, Set<String> requestedCodes, Long callerUserId) {
        Set<String> known = permissionRepository.findAll().stream()
                .map(Permission::getCode).collect(Collectors.toSet());
        Set<String> codes = new TreeSet<>(requestedCodes == null ? Set.of() : requestedCodes);
        Set<String> unknown = codes.stream().filter(c -> !known.contains(c))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!unknown.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown permission code(s): " + unknown);
        }

        Long roleId = jdbcTemplate.query(
                "SELECT id FROM roles WHERE code = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                roleCode.name());
        if (roleId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown role: " + roleCode);
        }

        Set<String> previous = new TreeSet<>(jdbcTemplate.queryForList("""
                SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                 WHERE rp.role_id = ?
                """, String.class, roleId));

        jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
        for (String code : codes) {
            jdbcTemplate.update("""
                    INSERT INTO role_permissions (role_id, permission_id)
                    SELECT ?, id FROM permissions WHERE code = ?
                    """, roleId, code);
        }

        auditLogService.record(callerUserId, "ROLE_PERMISSIONS_CHANGED", "Role", roleId, previous, codes);
        return matrix();
    }

    /** Union of the granted codes across a set of roles — the check a future
     * {@code @PreAuthorize("@perms.has('X')")} resolves against. */
    @Transactional(readOnly = true)
    public Set<String> codesForRoles(Collection<RoleCode> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        List<String> names = roleCodes.stream().map(Enum::name).toList();
        String placeholders = String.join(",", names.stream().map(n -> "?").toList());
        List<String> codes = jdbcTemplate.queryForList("""
                SELECT DISTINCT p.code
                  FROM role_permissions rp
                  JOIN roles r ON r.id = rp.role_id
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE r.code IN (%s)
                """.formatted(placeholders), String.class, names.toArray());
        return new java.util.HashSet<>(codes);
    }
}
