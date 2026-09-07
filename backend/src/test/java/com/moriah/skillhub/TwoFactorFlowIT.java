package com.moriah.skillhub;

import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import com.moriah.skillhub.common.util.Base32Codec;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * build-plan.md feature 05 verify line: "An ADMIN without 2FA cannot complete login" —
 * generalized here to the full lifecycle for any account: enable -> confirm -> a subsequent login
 * is gated behind a challenge, not a direct token pair -> wrong code doesn't consume the challenge
 * (retryable) -> correct code completes login and consumes it (not reusable) -> disable requires
 * a valid code -> login goes back to issuing a direct token pair once disabled.
 * <p>
 * TOTP codes are computed locally with a small re-implementation of the RFC 6238 algorithm
 * (below), the same pattern {@code AuthFlowIT} uses for verification/reset tokens: {@link
 * com.moriah.skillhub.common.security.TotpService} deliberately exposes no code-generation
 * method (only {@code verifyCode}) — the server only ever verifies a code an authenticator app
 * produced, never generates one itself, and this test shouldn't be the reason that stops being
 * true. {@code TotpServiceTest} already validates the real implementation against RFC 4226's
 * published test vectors; this is test scaffolding, not a second copy of what's under test.
 */
class TwoFactorFlowIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void fullLifecycle_enableConfirmLoginChallengeThenDisable() {
        String email = uniqueEmail();
        String password = "correct horse battery";
        registerAndVerify(email, password);

        String accessToken = login(email, password)
                .then()
                .statusCode(200)
                .body("data.twoFactorRequired", equalTo(false))
                .extract().response()
                .jsonPath().getString("data.tokens.accessToken");

        // enable — secret generated, but not yet enforced until confirmed
        Response enableResponse = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of())
            .when()
                .post("/api/v1/auth/2fa/enable")
            .then()
                .statusCode(200)
                .body("data.secret", notNullValue())
                .body("data.provisioningUri", startsWith("otpauth://totp/"))
                .extract().response();

        byte[] secret = Base32Codec.decode(enableResponse.jsonPath().getString("data.secret"));

        // a login attempt right now, before confirmation, is still ungated
        login(email, password).then().statusCode(200).body("data.twoFactorRequired", equalTo(false));

        // confirm setup (no challengeToken — identified by the caller's own access token)
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("totpCode", currentCode(secret)))
            .when()
                .post("/api/v1/auth/2fa/verify")
            .then()
                .statusCode(200)
                .body("data.tokens", nullValue());

        // now login is gated behind a challenge, not a direct token pair
        Response loginResponse = login(email, password)
                .then()
                .statusCode(200)
                .body("data.twoFactorRequired", equalTo(true))
                .body("data.challengeToken", notNullValue())
                .body("data.tokens", nullValue())
                .extract().response();
        String challengeToken = loginResponse.jsonPath().getString("data.challengeToken");

        // wrong code: challenge stays alive, retryable
        given()
                .contentType("application/json")
                .body(Map.of("challengeToken", challengeToken, "totpCode", "000000"))
            .when()
                .post("/api/v1/auth/2fa/verify")
            .then()
                .statusCode(401)
                .body("error.code", equalTo("INVALID_2FA_CODE"));

        // correct code completes login
        String newAccessToken = given()
                .contentType("application/json")
                .body(Map.of("challengeToken", challengeToken, "totpCode", currentCode(secret)))
            .when()
                .post("/api/v1/auth/2fa/verify")
            .then()
                .statusCode(200)
                .body("data.tokens.accessToken", notNullValue())
                .extract().response()
                .jsonPath().getString("data.tokens.accessToken");

        // the challenge token is single-use — reusing it fails even with a fresh correct code
        given()
                .contentType("application/json")
                .body(Map.of("challengeToken", challengeToken, "totpCode", currentCode(secret)))
            .when()
                .post("/api/v1/auth/2fa/verify")
            .then()
                .statusCode(401)
                .body("error.code", equalTo("INVALID_2FA_CHALLENGE"));

        // disable requires a valid code, not just an authenticated call
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + newAccessToken)
                .body(Map.of("totpCode", "000000"))
            .when()
                .post("/api/v1/auth/2fa/disable")
            .then()
                .statusCode(401);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + newAccessToken)
                .body(Map.of("totpCode", currentCode(secret)))
            .when()
                .post("/api/v1/auth/2fa/disable")
            .then()
                .statusCode(200);

        // login is ungated again
        login(email, password).then().statusCode(200).body("data.twoFactorRequired", equalTo(false));
    }

    @Test
    void enableTwiceWithoutDisabling_returnsConflict() {
        String email = uniqueEmail();
        String password = "correct horse battery";
        registerAndVerify(email, password);
        String accessToken = login(email, password).jsonPath().getString("data.tokens.accessToken");

        Response enableResponse = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of())
            .when()
                .post("/api/v1/auth/2fa/enable")
            .then()
                .statusCode(200)
                .extract().response();
        byte[] secret = Base32Codec.decode(enableResponse.jsonPath().getString("data.secret"));

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("totpCode", currentCode(secret)))
            .when()
                .post("/api/v1/auth/2fa/verify")
            .then()
                .statusCode(200);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of())
            .when()
                .post("/api/v1/auth/2fa/enable")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("TWO_FACTOR_ALREADY_ENABLED"));
    }

    @Test
    void enableWithoutAuthentication_returns401() {
        given()
                .contentType("application/json")
                .body(Map.of())
            .when()
                .post("/api/v1/auth/2fa/enable")
            .then()
                .statusCode(401);
    }

    /** build-plan.md feature 05 verify line, literally: "An ADMIN without 2FA cannot complete
     * login." An ADMIN/HR_MANAGER account with no 2FA set up yet has no access token to
     * authenticate {@code /2fa/enable} with — the login challenge token stands in for one
     * (`/review` follow-up: this enforcement didn't exist at all before this test). */
    @Test
    void adminWithoutTwoFactor_isForcedThroughMandatorySetupBeforeReceivingTokens() {
        String email = uniqueEmail();
        String password = "correct horse battery";
        registerAndVerify(email, password);
        grantRole(email, "ADMIN");

        // login as ADMIN with no 2FA configured — must be challenged with setup required, not
        // issued a token pair directly, and not simply let through.
        Response loginResponse = login(email, password)
                .then()
                .statusCode(200)
                .body("data.twoFactorRequired", equalTo(true))
                .body("data.twoFactorSetupRequired", equalTo(true))
                .body("data.challengeToken", notNullValue())
                .body("data.tokens", nullValue())
                .extract().response();
        String challengeToken = loginResponse.jsonPath().getString("data.challengeToken");

        // no access token exists yet — /2fa/enable is authenticated by the challenge token instead
        Response enableResponse = given()
                .contentType("application/json")
                .body(Map.of("challengeToken", challengeToken))
            .when()
                .post("/api/v1/auth/2fa/enable")
            .then()
                .statusCode(200)
                .body("data.secret", notNullValue())
                .extract().response();
        byte[] secret = Base32Codec.decode(enableResponse.jsonPath().getString("data.secret"));

        // the first correct code against that secret both confirms setup AND completes login
        String accessToken = given()
                .contentType("application/json")
                .body(Map.of("challengeToken", challengeToken, "totpCode", currentCode(secret)))
            .when()
                .post("/api/v1/auth/2fa/verify")
            .then()
                .statusCode(200)
                .body("data.tokens.accessToken", notNullValue())
                .extract().response()
                .jsonPath().getString("data.tokens.accessToken");
        assertThat(accessToken).isNotBlank();

        // subsequent logins are challenged as a normal already-enabled account — setup is no
        // longer required, only the existing code
        login(email, password)
                .then()
                .statusCode(200)
                .body("data.twoFactorRequired", equalTo(true))
                .body("data.twoFactorSetupRequired", equalTo(false));
    }

    @Test
    void hrManagerWithoutTwoFactor_isAlsoChallenged() {
        String email = uniqueEmail();
        String password = "correct horse battery";
        registerAndVerify(email, password);
        grantRole(email, "HR_MANAGER");

        login(email, password)
                .then()
                .statusCode(200)
                .body("data.twoFactorRequired", equalTo(true))
                .body("data.twoFactorSetupRequired", equalTo(true));
    }

    @Test
    void studentWithoutTwoFactor_isNeverChallenged() {
        String email = uniqueEmail();
        String password = "correct horse battery";
        registerAndVerify(email, password); // registers as STUDENT by default — no role grant

        login(email, password)
                .then()
                .statusCode(200)
                .body("data.twoFactorRequired", equalTo(false))
                .body("data.tokens.accessToken", notNullValue());
    }

    private void grantRole(String email, String roleCode) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private void registerAndVerify(String email, String password) {
        given()
            .contentType("application/json")
            .body(Map.of("fullName", "2FA Test User", "email", email, "password", password, "agreedToTerms", true))
        .when()
            .post("/api/v1/auth/register")
        .then()
            .statusCode(201);

        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        String raw = OpaqueTokenGenerator.generate();
        jdbcTemplate.update("""
                INSERT INTO email_verification_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, userId, OpaqueTokenGenerator.sha256Hex(raw), Instant.now().plus(1, ChronoUnit.HOURS));

        given()
            .contentType("application/json")
            .body(Map.of("token", raw))
        .when()
            .post("/api/v1/auth/verify-email")
        .then()
            .statusCode(200);
    }

    private Response login(String email, String password) {
        return given()
                .contentType("application/json")
                .body(Map.of("email", email, "password", password))
            .when()
                .post("/api/v1/auth/login");
    }

    private String uniqueEmail() {
        return "2fa-flow-" + UUID.randomUUID() + "@example.com";
    }

    // --- RFC 6238 TOTP, reimplemented minimally for test scaffolding only — see class Javadoc.

    private String currentCode(byte[] secret) {
        long step = Instant.now().getEpochSecond() / 30;
        return computeCode(secret, step);
    }

    private String computeCode(byte[] secret, long timeStepIndex) {
        byte[] stepBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            stepBytes[i] = (byte) (timeStepIndex & 0xFF);
            timeStepIndex >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(stepBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Test TOTP computation failed", e);
        }
    }
}
