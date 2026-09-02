package com.moriah.skillhub.auth.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The single-use, time-boxed token behind a staff accept-invite link — same shape as
 * {@link EmailVerificationToken} / {@code PasswordResetToken} (see {@code
 * V19__staff_invite_and_client_approval.sql}). Only the SHA-256 hex digest of the raw token is
 * stored; the raw value lives only in the emailed link.
 */
@Entity
@Table(name = "staff_invite_tokens")
@Getter
@Setter
@NoArgsConstructor
public class StaffInviteToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    // columnDefinition matches V1's CHAR(64) exactly — see RefreshToken for why.
    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
