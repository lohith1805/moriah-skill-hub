package com.moriah.skillhub.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

/**
 * For the rare place that needs the caller's identity outside a controller method parameter
 * (where {@link CurrentUser} is preferred) — e.g. a service called from more than one entry
 * point. Returns empty for system-initiated calls (scheduled jobs) that have no caller.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<Long> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal.userId());
    }

    /** `/architect feature 10`: {@code BatchService} needs to know whether the caller is an
     * {@code ADMIN} (full oversight, bypasses per-batch PM ownership) purely from the JWT's
     * already-decoded roles claim — no DB round trip, same reasoning as {@link #currentUserId}. */
    public static List<String> currentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            return List.of();
        }
        return principal.roles();
    }
}
