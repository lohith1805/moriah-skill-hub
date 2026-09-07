package com.moriah.skillhub.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code encryptionKey} is a base64-encoded 256-bit AES key, sourced today from a bare env var
 * (`TOTP_ENCRYPTION_KEY`) — the same treatment as {@code JWT_SECRET} and every DB password in
 * this project. architecture.md's original wording called for "a key from the secret store (not
 * a bare env var)"; no secret store (Vault, AWS Secrets Manager, ...) exists anywhere in this
 * project's approved dependencies or docker-compose, so this is a deliberate, user-confirmed
 * deviation (`/architect feature 05`), consistent with how DR/backup targets are handled
 * elsewhere in this build (no real cloud target chosen yet — fall back to the local/env-var
 * equivalent, documented, revisit later).
 */
@ConfigurationProperties(prefix = "moriah.totp")
public record TotpProperties(String encryptionKey) {
}
