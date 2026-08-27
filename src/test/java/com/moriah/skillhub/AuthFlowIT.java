package com.moriah.skillhub;

import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * build-plan.md feature 03 verify line: "Register -> verify email -> login -> protected call ->
 * refresh -> old refresh token rejected -> logout-all -> every previously issued access token
 * rejected within a second, not after 60 minutes." Exercised as one continuous flow, in order,
 * matching how a real client would use these endpoints.
 * <p>
 * Verification and password-reset tokens are never exposed by the API (the raw value is
 * intentionally never logged either — see AuthService's class Javadoc), so tests that need a
 * known raw token insert one directly via JDBC with a hash computed the same way the app does,
 * rather than trying to intercept one through the HTTP layer.
 */
class AuthFlowIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void register_createsUserWithStudentRoleAndVerificationToken() {
        String email = uniqueEmail();

        given()
            .contentType("application/json")
            .body(Map.of("fullName", "Ada Lovelace", "email", email, "password", "correct horse battery"))
        .when()
            .post("/api/v1/auth/register")
        .then()
            .statusCode(201)
            .body("data.email", equalTo(email))
            .body("data.uuid", notNullValue());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status FROM users WHERE email = ?", email);
        assertThat(row.get("status")).isEqualTo("PENDING_VERIFICATION");

        Long roleCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_roles ur
                JOIN users u ON u.id = ur.user_id
                JOIN roles r ON r.id = ur.role_id
                WHERE u.email = ? AND r.code = 'STUDENT'
                """, Long.class, email);
        assertThat(roleCount).isEqualTo(1);

        Long tokenCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM email_verification_tokens evt
                JOIN users u ON u.id = evt.user_id WHERE u.email = ?
                """, Long.class, email);
        assertThat(tokenCount).isEqualTo(1);

        // Feature 08 clears the "email delivery is a tracked stub" note — registration now
        // enqueues a real EMAIL notification via NotificationService.enqueueAfterCommit. Polled,
        // not asserted synchronously — same reasoning as assertInvoiceEventuallyIssued in
        // PaymentWebhookFlowIT.
        Map<String, Object> notification = waitForNotification(email, "EMAIL_VERIFICATION");
        assertThat(notification.get("channel")).isEqualTo("EMAIL");
        assertThat(notification.get("template_code")).isEqualTo("EMAIL_VERIFICATION");
    }

    @Test
    void login_beforeEmailVerification_returns403AccountNotVerified() {
        String email = registerUser();

        given()
            .contentType("application/json")
            .body(Map.of("email", email, "password", "correct horse battery"))
        .when()
            .post("/api/v1/auth/login")
        .then()
            .statusCode(403)
            .body("error.code", equalTo("ACCOUNT_NOT_VERIFIED"));
    }

    @Test
    void login_wrongPassword_returns401InvalidCredentials() {
        String email = registerUser();
        verifyEmailDirectly(email);

        given()
            .contentType("application/json")
            .body(Map.of("email", email, "password", "wrong password entirely"))
        .when()
            .post("/api/v1/auth/login")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("INVALID_CREDENTIALS"));

        // build-plan.md feature 03: "Failed logins written to audit_logs with IP." RestAssured
        // hits the embedded server over loopback, so the client IP ClientIpResolver resolves to
        // (no trusted proxy configured in application-test.yml) is deterministically 127.0.0.1.
        Map<String, Object> auditRow = jdbcTemplate.queryForMap("""
                SELECT al.ip_address FROM audit_logs al
                JOIN users u ON u.id = al.user_id
                WHERE u.email = ? AND al.action = 'LOGIN_FAILED'
                """, email);
        assertThat(auditRow.get("ip_address")).isEqualTo("127.0.0.1");
    }

    @Test
    void fullFlow_registerVerifyLoginProtectedCallRefreshLogoutAll() {
        String email = registerUser();
        verifyEmailDirectly(email);

        // login
        Response loginResponse = given()
                .contentType("application/json")
                .body(Map.of("email", email, "password", "correct horse battery"))
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(200)
                .body("data.twoFactorRequired", equalTo(false))
                .body("data.tokens.accessToken", notNullValue())
                .body("data.tokens.refreshToken", notNullValue())
                .extract().response();

        String accessToken = loginResponse.jsonPath().getString("data.tokens.accessToken");
        String refreshToken1 = loginResponse.jsonPath().getString("data.tokens.refreshToken");

        // protected call — no route exists yet, but authentication itself must succeed (404
        // "no handler", not 401 "unauthenticated" — the meaningful distinction here)
        given()
                .header("Authorization", "Bearer " + accessToken)
            .when()
                .get("/api/v1/some-future-protected-endpoint")
            .then()
                .statusCode(404);

        given()
            .when()
                .get("/api/v1/some-future-protected-endpoint")
            .then()
                .statusCode(401);

        // refresh — rotates the token
        Response refreshResponse = given()
                .contentType("application/json")
                .body(Map.of("refreshToken", refreshToken1))
            .when()
                .post("/api/v1/auth/refresh")
            .then()
                .statusCode(200)
                .extract().response();

        String refreshToken2 = refreshResponse.jsonPath().getString("data.refreshToken");
        assertThat(refreshToken2).isNotEqualTo(refreshToken1);

        // old refresh token rejected on reuse
        given()
                .contentType("application/json")
                .body(Map.of("refreshToken", refreshToken1))
            .when()
                .post("/api/v1/auth/refresh")
            .then()
                .statusCode(401)
                .body("error.code", equalTo("INVALID_REFRESH_TOKEN"));

        // reuse of an already-rotated refresh token revokes the whole chain and is recorded —
        // this is the "token theft" signal, not routine housekeeping, so it must be auditable.
        Long reuseAuditCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_logs al
                JOIN users u ON u.id = al.user_id
                WHERE u.email = ? AND al.action = 'REFRESH_TOKEN_REUSE_DETECTED'
                """, Long.class, email);
        assertThat(reuseAuditCount).isEqualTo(1);

        // the entire chain was revoked by the reuse, not just the reused token — the just-rotated
        // refreshToken2 must already be unusable even though it was never itself reused
        given()
                .contentType("application/json")
                .body(Map.of("refreshToken", refreshToken2))
            .when()
                .post("/api/v1/auth/refresh")
            .then()
                .statusCode(401);

        // logout-all
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .body(Map.of("refreshToken", refreshToken2))
            .when()
                .post("/api/v1/auth/logout-all")
            .then()
                .statusCode(200);

        // every previously issued access token rejected within a second, not after 60 minutes
        given()
                .header("Authorization", "Bearer " + accessToken)
            .when()
                .get("/api/v1/some-future-protected-endpoint")
            .then()
                .statusCode(401);

        // the rotated-but-now-revoked refresh token can no longer be used either
        given()
                .contentType("application/json")
                .body(Map.of("refreshToken", refreshToken2))
            .when()
                .post("/api/v1/auth/refresh")
            .then()
                .statusCode(401);
    }

    @Test
    void resetPassword_invalidatesOldPasswordAcceptsNewOneAndWritesAuditLog() {
        String email = registerUser();
        verifyEmailDirectly(email);

        // forgot-password always responds the same way regardless of whether the email exists
        // (no account-enumeration signal) — asserted here, then the reset itself is driven by a
        // directly-inserted token, same reasoning as verifyEmailDirectly: the raw token is never
        // exposed by the API or logged.
        given()
            .contentType("application/json")
            .body(Map.of("email", email))
        .when()
            .post("/api/v1/auth/password/forgot")
        .then()
            .statusCode(200);

        // Feature 08 clears the "password-reset delivery is a tracked stub" note.
        Map<String, Object> notification = waitForNotification(email, "PASSWORD_RESET");
        assertThat(notification.get("channel")).isEqualTo("EMAIL");

        String newPassword = "a brand new correct horse battery";
        resetPasswordDirectly(email, newPassword);

        // old password no longer works
        given()
            .contentType("application/json")
            .body(Map.of("email", email, "password", "correct horse battery"))
        .when()
            .post("/api/v1/auth/login")
        .then()
            .statusCode(401)
            .body("error.code", equalTo("INVALID_CREDENTIALS"));

        // new password works
        given()
            .contentType("application/json")
            .body(Map.of("email", email, "password", newPassword))
        .when()
            .post("/api/v1/auth/login")
        .then()
            .statusCode(200)
            .body("data.tokens.accessToken", notNullValue());

        // build-plan.md feature 03 revocation contract — a password reset must be auditable the
        // same way a suspicious refresh-token reuse or a failed login is.
        Long resetAuditCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_logs al
                JOIN users u ON u.id = al.user_id
                WHERE u.email = ? AND al.action = 'PASSWORD_RESET'
                """, Long.class, email);
        assertThat(resetAuditCount).isEqualTo(1);
    }

    private String registerUser() {
        String email = uniqueEmail();
        given()
            .contentType("application/json")
            .body(Map.of("fullName", "Test User", "email", email, "password", "correct horse battery"))
        .when()
            .post("/api/v1/auth/register")
        .then()
            .statusCode(201);
        return email;
    }

    /** Bypasses the HTTP verify-email endpoint's opaque-token requirement for tests that only
     * need an already-verified account, by inserting a token with a known raw value directly. */
    private void verifyEmailDirectly(String email) {
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

    /** Same reasoning as {@link #verifyEmailDirectly}: the raw password-reset token is never
     * exposed by the API or logged, so tests insert one directly via JDBC with a hash computed
     * the same way the app does. */
    private void resetPasswordDirectly(String email, String newPassword) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        String raw = OpaqueTokenGenerator.generate();
        jdbcTemplate.update("""
                INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, userId, OpaqueTokenGenerator.sha256Hex(raw), Instant.now().plus(1, ChronoUnit.HOURS));

        given()
            .contentType("application/json")
            .body(Map.of("token", raw, "newPassword", newPassword))
        .when()
            .post("/api/v1/auth/password/reset")
        .then()
            .statusCode(200);
    }

    private String uniqueEmail() {
        return "auth-flow-" + UUID.randomUUID() + "@example.com";
    }

    /** Polls up to 10s for {@code NotificationService.enqueueAfterCommit}'s row to land — same
     * pattern as {@code PaymentWebhookFlowIT.assertInvoiceEventuallyIssued}. */
    private Map<String, Object> waitForNotification(String email, String templateCode) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT n.channel, n.template_code, n.status FROM notifications n
                    JOIN users u ON u.id = n.user_id WHERE u.email = ? AND n.template_code = ?
                    """, email, templateCode);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while polling for notification", e);
            }
        }
        throw new AssertionError("No " + templateCode + " notification found for " + email + " within 10s");
    }
}
