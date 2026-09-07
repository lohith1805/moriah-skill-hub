package com.moriah.skillhub.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM at-rest encryption for {@code users.two_factor_secret} — architecture.md
 * "Authentication": "secret AES-GCM encrypted... not a bare env var" (the key-sourcing part of
 * that line is a documented, confirmed deviation; see {@link TotpProperties}). A random 12-byte
 * IV is generated per encryption and stored alongside the ciphertext (IV || ciphertext || GCM
 * tag, in that order) — GCM must never reuse an IV under the same key, so a fixed or
 * counter-shared IV is not an option here.
 */
@Service
@RequiredArgsConstructor
public class TotpSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final TotpProperties totpProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            return ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt TOTP secret", e);
        }
    }

    /** Throws {@link IllegalStateException} (wrapping {@link AEADBadTagException}) if the blob
     * was tampered with or encrypted under a different key — GCM's authentication tag makes this
     * detectable, not just a garbled decrypt. */
    public byte[] decrypt(byte[] blob) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(blob);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt TOTP secret", e);
        }
    }

    private SecretKeySpec key() {
        byte[] keyBytes = Base64.getDecoder().decode(totpProperties.encryptionKey());
        return new SecretKeySpec(keyBytes, "AES");
    }
}
