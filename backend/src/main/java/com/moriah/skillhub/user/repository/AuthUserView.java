package com.moriah.skillhub.user.repository;

import com.moriah.skillhub.user.entity.UserStatus;

/**
 * Audit 2026-08-31 (H7): the minimal read {@code JwtAuthFilter} needs on every authenticated
 * request — id, uuid, status, token_version — as a Spring Data projection so the filter no
 * longer loads the full {@code User} entity (all ~15 columns, including the {@code byte[]} TOTP
 * secret) per request just to compare one integer. The {@code token_version} check still hits the
 * database fresh on every request, as designed; it is just cheaper now.
 */
public interface AuthUserView {

    Long getId();

    String getUuid();

    UserStatus getStatus();

    Integer getTokenVersion();
}
