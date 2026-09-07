package com.moriah.skillhub.common.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verified against RFC 4226 Appendix D's published test vectors — HOTP(K, C) and TOTP(K, T) share
 * the exact same HMAC + dynamic-truncation algorithm (RFC 6238 §4), TOTP just substitutes a time
 * step for the counter. With a 30-second step, epoch second {@code counter * 30} produces step
 * index {@code counter}, so fixing {@link TotpService}'s clock to each of those instants
 * reproduces RFC 4226's counters 0-9 exactly. The secret is the RFC's own ASCII test key
 * ("12345678901234567890"), never used for anything real.
 */
class TotpServiceTest {

    private static final byte[] RFC_4226_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String[] RFC_4226_CODES = {
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489"
    };

    @Test
    void verifyCode_matchesRfc4226TestVectorsAtTheirCorrespondingTimeStep() {
        for (int counter = 0; counter < RFC_4226_CODES.length; counter++) {
            TotpService totpService = new TotpService(clockAtEpochSecond(counter * 30L));
            assertThat(totpService.verifyCode(RFC_4226_SECRET, RFC_4226_CODES[counter]))
                    .as("counter %d", counter)
                    .isTrue();
        }
    }

    @Test
    void verifyCode_wrongCode_returnsFalse() {
        TotpService totpService = new TotpService(clockAtEpochSecond(0));
        assertThat(totpService.verifyCode(RFC_4226_SECRET, "000000")).isFalse();
    }

    @Test
    void verifyCode_malformedCode_returnsFalseRatherThanThrowing() {
        TotpService totpService = new TotpService(clockAtEpochSecond(0));
        assertThat(totpService.verifyCode(RFC_4226_SECRET, "12345")).isFalse(); // too short
        assertThat(totpService.verifyCode(RFC_4226_SECRET, "abcdef")).isFalse(); // not digits
        assertThat(totpService.verifyCode(RFC_4226_SECRET, null)).isFalse();
    }

    @Test
    void verifyCode_toleratesOneStepOfClockDrift() {
        // Counter 0's code ("755224") verified against a clock one step (30s) later — still
        // within the ±1 step drift window this service allows.
        TotpService totpServiceOneStepLater = new TotpService(clockAtEpochSecond(30));
        assertThat(totpServiceOneStepLater.verifyCode(RFC_4226_SECRET, RFC_4226_CODES[0])).isTrue();
    }

    @Test
    void verifyCode_rejectsCodeTwoStepsOutsideTheDriftWindow() {
        TotpService totpServiceTwoStepsLater = new TotpService(clockAtEpochSecond(60));
        assertThat(totpServiceTwoStepsLater.verifyCode(RFC_4226_SECRET, RFC_4226_CODES[0])).isFalse();
    }

    @Test
    void buildProvisioningUri_isAWellFormedOtpauthUri() {
        TotpService totpService = new TotpService(clockAtEpochSecond(0));
        String uri = totpService.buildProvisioningUri(RFC_4226_SECRET, "student@example.com", "Moriah Skill Hub");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=");
        assertThat(uri).contains("issuer=");
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
    }

    private Clock clockAtEpochSecond(long epochSecond) {
        return Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
    }
}
