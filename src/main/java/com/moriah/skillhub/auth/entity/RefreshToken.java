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
 * {@code token_hash} is a SHA-256 hex digest — the raw opaque token exists only in the response
 * body (library-docs.md JJWT rules; the same discipline applies to this non-JWT refresh token).
 * {@code replaced_by} is a plain id, not a self-referencing association — it is only ever
 * compared, never navigated, and a bare {@code Long} keeps the reused-token-detection check in
 * {@code AuthService} a simple equality check instead of a lazy-load.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    // columnDefinition matches V1's CHAR(64) exactly — a SHA-256 hex digest is always exactly
    // 64 characters, and Hibernate's default for a String column is VARCHAR, not CHAR.
    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private Long replacedBy;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
