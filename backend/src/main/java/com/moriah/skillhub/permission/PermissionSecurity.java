package com.moriah.skillhub.permission;

import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.user.entity.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * SpEL entry point for method security: {@code @PreAuthorize("@perms.has('OFFER_GENERATE')")}.
 * Resolves the caller's roles from the JWT (no DB round trip for the roles themselves) and unions
 * their granted permission codes (one indexed query). Not yet referenced by any endpoint — the
 * hook is here so future gating is a one-line annotation, not a framework build.
 */
@Component("perms")
@RequiredArgsConstructor
public class PermissionSecurity {

    private final PermissionService permissionService;

    public boolean has(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        Set<RoleCode> roles = EnumSet.noneOf(RoleCode.class);
        for (String name : SecurityUtils.currentUserRoles()) {
            try {
                roles.add(RoleCode.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // an authority that isn't a RoleCode (shouldn't happen) — skip it
            }
        }
        return permissionService.codesForRoles(roles).contains(code);
    }
}
