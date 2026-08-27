package com.moriah.skillhub.common.storage;

import java.util.Map;

/**
 * Hand-rolled magic-byte sniffing — no file-type-detection library is in code-standards.md's
 * approved dependency list, and the check is small enough that adding one fails "is there a
 * simpler solution with what is already here?" (same reasoning as TOTP/Base32 in feature 05).
 * Covers every content type architecture.md's storage key layout actually needs: PDFs for
 * resumes/certificates/invoices/payslips/HR documents, JPEG/PNG for project assets. Extend this
 * map, not the validation logic, if a later feature needs another type.
 */
final class FileSignatures {

    private FileSignatures() {
    }

    /** content type → magic bytes the file must start with. */
    private static final Map<String, byte[]> SIGNATURES = Map.of(
            "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46}, // %PDF
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
    );

    static boolean isSupported(String contentType) {
        return SIGNATURES.containsKey(contentType);
    }

    /** {@code false} for an unrecognized content type too — an unsupported type should be
     * rejected by {@link #isSupported} first, not silently pass the magic-byte check. */
    static boolean matches(String contentType, byte[] content) {
        byte[] signature = SIGNATURES.get(contentType);
        if (signature == null || content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
