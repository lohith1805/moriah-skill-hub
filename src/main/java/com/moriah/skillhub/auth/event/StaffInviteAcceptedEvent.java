package com.moriah.skillhub.auth.event;

/**
 * Published by {@link com.moriah.skillhub.auth.AuthService#acceptInvite} the moment an invited
 * staff account flips {@code INVITED -> ACTIVE}. HR listens for it (AFTER_COMMIT) to auto-provision
 * a bare {@code employees} row, so a new hire can use self-service attendance / leave straight away
 * without waiting for HR to onboard them manually. Carries only the internal user id — the listener
 * re-reads everything it needs.
 */
public record StaffInviteAcceptedEvent(Long userId) {
}
