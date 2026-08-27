package com.moriah.skillhub.crm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * build-plan.md feature 18: {@code dedupe_hash = SHA-256 of normalised email + phone}. A pure
 * function, no DB read — same shape as {@code ProjectService.slugify()} after its own `/review`
 * simplification (progress-tracker.md feature 15).
 * <p>
 * {@link #normalizePhone} (digits only, no {@code +}) is also what {@link LeadService} stores in
 * {@code leads.phone} itself, not just what feeds the hash — the WhatsApp Cloud API's inbound
 * {@code messages[].from} is always digits-only with no {@code +} (Meta's convention), so
 * {@code WhatsAppWebhookService}'s lead-by-phone lookup only matches if the stored column uses
 * the exact same normalization, not whatever punctuation the agent originally typed — hence
 * {@code public}, unlike a typical package-private helper, so {@code crm.webhook} can reuse it.
 */
public final class LeadDedupeHasher {

    private LeadDedupeHasher() {
    }

    public static String hash(String email, String phone) {
        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
        String input = normalisedEmail + "|" + normalizePhone(phone);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }

    public static String normalizePhone(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }
}
