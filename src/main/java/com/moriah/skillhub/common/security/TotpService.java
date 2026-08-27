package com.moriah.skillhub.common.security;

import com.moriah.skillhub.common.util.Base32Codec;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30s step) — implemented directly against JDK crypto rather
 * than a third-party library, per the `/architect feature 05` decision: no TOTP library is in
 * code-standards.md's approved dependency list, and the algorithm itself is small and stable
 * enough that adding a dependency for it fails "is there a simpler solution with what is already
 * here?". Compatible with every standard authenticator app (Google Authenticator, Authy, 1Password,
 * ...) — this is the exact algorithm and parameter set they all implement.
 * <p>
 * The secret itself is never handled by this class in encrypted form — {@link TotpSecretCipher}
 * owns AES-GCM at-rest encryption; this class only ever sees the raw secret bytes, for exactly as
 * long as one computation takes.
 */
@Service
public class TotpService {

    private static final int SECRET_BYTES = 20; // 160 bits — the RFC 4226/6238-recommended HMAC-SHA1 key size
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    /** Tolerates up to one step (30s) of clock drift either side of "now" — standard practice,
     * since a phone's clock and this server's clock are never perfectly synchronized. */
    private static final int ALLOWED_STEP_DRIFT = 1;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    public TotpService() {
        this(Clock.systemUTC());
    }

    /** Package-private — lets {@code TotpServiceTest} fix "now" to verify against known RFC
     * 4226/6238 test vectors instead of racing the real clock. The Spring-managed instance always
     * uses the public no-arg constructor above. */
    TotpService(Clock clock) {
        this.clock = clock;
    }

    public byte[] generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return secret;
    }

    /** {@code otpauth://} Key URI Format (the de facto standard every authenticator app's QR
     * scanner understands) — {@code issuer} and {@code accountName} both appear in the app's UI,
     * so the user can tell which account this entry belongs to. */
    public String buildProvisioningUri(byte[] secret, String accountEmail, String issuer) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String encodedLabel = URLEncoder.encode(issuer + ":" + accountEmail, StandardCharsets.UTF_8);
        return "otpauth://totp/" + encodedLabel
                + "?secret=" + Base32Codec.encode(secret)
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1"
                + "&digits=" + CODE_DIGITS
                + "&period=" + TIME_STEP_SECONDS;
    }

    /** Checks {@code code} against the current time step and {@link #ALLOWED_STEP_DRIFT} steps
     * either side of it. Returns as soon as any step in the window matches — never leaks which
     * offset matched. */
    public boolean verifyCode(byte[] secret, String code) {
        if (code == null || !code.matches("\\d{" + CODE_DIGITS + "}")) {
            return false;
        }
        long currentStep = Instant.now(clock).getEpochSecond() / TIME_STEP_SECONDS;
        for (int drift = -ALLOWED_STEP_DRIFT; drift <= ALLOWED_STEP_DRIFT; drift++) {
            if (computeCode(secret, currentStep + drift).equals(code)) {
                return true;
            }
        }
        return false;
    }

    private String computeCode(byte[] secret, long timeStepIndex) {
        byte[] stepBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            stepBytes[i] = (byte) (timeStepIndex & 0xFF);
            timeStepIndex >>= 8;
        }

        byte[] hash = hmacSha1(secret, stepBytes);

        // RFC 4226 dynamic truncation.
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        int otp = binary % (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", otp);
    }

    private byte[] hmacSha1(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException e) {
            // HmacSHA1 is a mandatory JDK algorithm (JLS platform guarantee) — this cannot happen.
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Invalid TOTP secret key", e);
        }
    }
}
