package com.moriah.skillhub.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Shared by every non-JWT token in the system (refresh, password reset, email verification) —
 * architecture.md: "opaque 512-bit value, SHA-256 hashed." The raw value is returned to the
 * caller once and never stored; only its hash is persisted (library-docs.md "JJWT" — the same
 * discipline applies here even though these aren't JWTs).
 */
public final class OpaqueTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OpaqueTokenGenerator() {
    }

    /** 512 bits of randomness, base64url-encoded for safe transport in a URL or JSON body. */
    public static String generate() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (JLS platform guarantee) — this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
