package com.moriah.skillhub.user.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The public identifier is {@code uuid} — {@code id} must never leave the service layer
 * (architecture.md invariant: "No endpoint exposes users.id").
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    // columnDefinition matches V1's CHAR(36) exactly — Hibernate's default for a String column
    // is VARCHAR, not CHAR (same fix as the *_tokens.token_hash columns — see RefreshToken).
    @Column(nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private String uuid = UUID.randomUUID().toString();

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "github_username", length = 100)
    private String githubUsername;

    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    /**
     * Incremented on suspend, password reset, role change, or logout-all. Every JWT carries it
     * as the {@code tv} claim; a mismatch rejects the token immediately (architecture.md
     * "Authentication"). Never read this column directly in a hot path — {@code
     * TokenRevocationService} caches it in Redis with a 60s TTL.
     */
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    @Column(name = "two_factor_secret")
    private byte[] twoFactorSecret;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
