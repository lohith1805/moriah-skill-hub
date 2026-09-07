package com.moriah.skillhub.common.util;

/**
 * RFC 4648 Base32 — not in the JDK ({@code java.util.Base64} has no Base32 counterpart) and no
 * approved dependency provides it (code-standards.md "Dependencies": a whole new library for a
 * ~20-line algorithm fails "Is there a simpler solution with what is already here?"). Needed
 * because every real TOTP authenticator app (Google Authenticator, Authy, ...) expects the
 * shared secret in an {@code otpauth://} provisioning URI to be Base32, not Base64 — this is
 * RFC 6238 / the de facto Google Authenticator Key URI Format, not a project-specific choice.
 */
public final class Base32Codec {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32Codec() {
    }

    public static String encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsInBuffer = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5;
                out.append(ALPHABET.charAt((buffer >> bitsInBuffer) & 0x1F));
            }
        }
        if (bitsInBuffer > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bitsInBuffer)) & 0x1F));
        }
        return out.toString();
    }

    public static byte[] decode(String base32) {
        String cleaned = base32.trim().toUpperCase().replace("=", "");
        byte[] out = new byte[cleaned.length() * 5 / 8];
        int buffer = 0;
        int bitsInBuffer = 0;
        int outIndex = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            int value = ALPHABET.indexOf(cleaned.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32 character: " + cleaned.charAt(i));
            }
            buffer = (buffer << 5) | value;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                out[outIndex++] = (byte) ((buffer >> bitsInBuffer) & 0xFF);
            }
        }
        return out;
    }
}
