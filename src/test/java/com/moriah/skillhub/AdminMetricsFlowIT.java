package com.moriah.skillhub;

import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import com.moriah.skillhub.common.util.Base32Codec;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * build-plan.md feature 22's own "Verify" line, exercised end to end over real HTTP: "Non-admin
 * gets 403 everywhere. Suspending a user invalidates their token within a second, verified by an
 * immediate second request with the old token." Plus the rest of this feature's endpoints. Same
 * structure as {@code BaClientFlowIT}/{@code CertificateFlowIT}: real JWT auth for every
 * authenticated call, fixture rows inserted directly via JdbcTemplate, and the mandatory-2FA
 * helper for {@code ADMIN} logins.
 * <p>
 * Every IT class in this suite shares one MySQL/Redis container across the whole {@code mvn
 * verify} run (IntegrationTestBase) — several other features' own IT classes already write real
 * rows into {@code student_metrics}/{@code leads}/{@code payments}/{@code audit_logs} before this
 * class ever runs. Assertions here are deliberately scoped to data this test itself creates
 * (a distinctive currency for the revenue check, a specific target user's uuid for the audit/user
 * checks) rather than exact global totals, which would be fragile against that shared, growing
 * state — the same reasoning {@code SeedIdempotencyIT} states explicitly for seed row counts.
 */
class AdminMetricsFlowIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // --- overview / revenue ---

    @Test
    void overview_returnsCorrectShapeFromRealData() {
        String adminToken = registerVerifyGrantRoleAndLogin("Admin Overview One", "ADMIN");

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/v1/admin/metrics/overview")
            .then()
                .statusCode(200)
                .body("data.activeStudentCount", greaterThanOrEqualTo(0))
                .body("data.recentRevenue", notNullValue())
                .body("data.leadFunnel", notNullValue())
                .body("data.totalPlannedPoints", greaterThanOrEqualTo(0))
                .body("data.totalCompletedPoints", greaterThanOrEqualTo(0))
                .body("data.overallVelocityRatio", notNullValue());
    }

    @Test
    void revenue_reflectsASeededCapturedPayment_isolatedByCurrency() {
        String adminToken = registerVerifyGrantRoleAndLogin("Admin Revenue One", "ADMIN");
        String studentToken = registerVerifyAndLogin("Rev Payer One");
        long userId = currentUserId(studentToken);
        long planId = jdbcTemplate.queryForObject("SELECT id FROM subscription_plans WHERE code = 'STARTER'", Long.class);

        // USD, not INR — isolates this row from every other IT class's captured payments (this
        // platform is INR-priced end to end, per architecture.md), so the exact amount asserted
        // below cannot be polluted by ambient data from the shared container.
        insertCapturedPayment(userId, planId, "USD", new BigDecimal("123.45"));

        String thisMonth = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now().withDayOfMonth(1));

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/v1/admin/metrics/revenue?from=" + thisMonth + "&to=" + thisMonth)
            .then()
                .statusCode(200)
                .body("data.find { it.currency == 'USD' }.totalCaptured", equalTo(123.45f));
    }

    // --- 403 for non-admin ---

    @Test
    void nonAdmin_getsForbidden_onEveryAdminEndpoint() {
        String studentToken = registerVerifyAndLogin("Admin Forbidden Student");

        given().header("Authorization", "Bearer " + studentToken).when()
                .get("/api/v1/admin/metrics/overview").then().statusCode(403);
        given().header("Authorization", "Bearer " + studentToken).when()
                .get("/api/v1/admin/metrics/revenue").then().statusCode(403);
        given().header("Authorization", "Bearer " + studentToken).when()
                .get("/api/v1/admin/users").then().statusCode(403);
        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + studentToken)
                .body(Map.of("status", "SUSPENDED")).when()
                .put("/api/v1/admin/users/" + UUID.randomUUID() + "/status").then().statusCode(403);
        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + studentToken)
                .body(Map.of("roles", List.of("STUDENT"))).when()
                .put("/api/v1/admin/users/" + UUID.randomUUID() + "/roles").then().statusCode(403);
        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + studentToken)
                .body(Map.of("name", "X", "priceInr", 1, "durationDays", 30,
                        "mentorSupport", false, "allowsBatch", false, "allowsSprints", false,
                        "allowsPip", false, "allowsInternshipLetter", false, "allowsClientProject", false,
                        "active", true))
                .when()
                .put("/api/v1/admin/plans/1").then().statusCode(403);
        given().header("Authorization", "Bearer " + studentToken).when()
                .get("/api/v1/admin/audit").then().statusCode(403);
        given().header("Authorization", "Bearer " + studentToken).when()
                .post("/api/v1/admin/exports/users").then().statusCode(403);
    }

    // --- users list ---

    @Test
    void usersList_filtersByRoleAndStatus() {
        String adminToken = registerVerifyGrantRoleAndLogin("Admin Users One", "ADMIN");
        String clientToken = registerVerifyGrantRoleAndLogin("Admin Users Client", "CLIENT");
        String clientUuid = uuidOf(currentUserId(clientToken));

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/v1/admin/users?role=CLIENT&status=ACTIVE")
            .then()
                .statusCode(200)
                .body("data.content.find { it.uuid == '" + clientUuid + "' }.status", equalTo("ACTIVE"))
                // Every registration also grants the default STUDENT role (AuthService.register)
                // on top of whichever role the test helper adds — both survive here since GET
                // /admin/users never mutates roles, unlike PUT .../roles below.
                .body("data.content.find { it.uuid == '" + clientUuid + "' }.roles", equalTo(List.of("CLIENT", "STUDENT")));

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/v1/admin/users?role=HR_MANAGER")
            .then()
                .statusCode(200)
                .body("data.content.uuid", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(clientUuid)));
    }

    // --- suspend / role change: token_version bump + immediate invalidation ---

    @Test
    void suspendUser_invalidatesTokenImmediately_andIsAudited() {
        String adminToken = registerVerifyGrantRoleAndLogin("Admin Suspend One", "ADMIN");
        String adminUuid = uuidOf(currentUserId(adminToken));
        String targetToken = registerVerifyAndLogin("Suspend Target One");
        String targetUuid = uuidOf(currentUserId(targetToken));

        // The stale token still works right up until the suspend call.
        given().header("Authorization", "Bearer " + targetToken).when()
                .get("/api/v1/users/me").then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("status", "SUSPENDED"))
            .when()
                .put("/api/v1/admin/users/" + targetUuid + "/status")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("SUSPENDED"));

        // Same, now-stale access token, immediately retried — build-plan.md's own verify line.
        given().header("Authorization", "Bearer " + targetToken).when()
                .get("/api/v1/users/me").then().statusCode(401);

        // audit_logs.user_id records the ACTOR (the admin who performed the suspend), never the
        // target — see AuditLogResponse's own Javadoc. entityUuid resolves the target side.
        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/v1/admin/audit?entityType=User&userUuid=" + adminUuid)
            .then()
                .statusCode(200)
                .body("data.content.find { it.action == 'USER_STATUS_CHANGED' }.userUuid", equalTo(adminUuid))
                .body("data.content.find { it.action == 'USER_STATUS_CHANGED' }.entityUuid", equalTo(targetUuid))
                .body("data.content.find { it.action == 'USER_STATUS_CHANGED' }.entityId", org.hamcrest.Matchers.nullValue());
    }

    @Test
    void roleChange_invalidatesTokenImmediately_evenThoughStatusStaysActive() {
        String adminToken = registerVerifyGrantRoleAndLogin("Admin RoleChange One", "ADMIN");
        String targetToken = registerVerifyAndLogin("RoleChange Target One");
        String targetUuid = uuidOf(currentUserId(targetToken));

        given().header("Authorization", "Bearer " + targetToken).when()
                .get("/api/v1/users/me").then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("roles", List.of("TRAINER_PM")))
            .when()
                .put("/api/v1/admin/users/" + targetUuid + "/roles")
            .then()
                .statusCode(200)
                .body("data.roles", equalTo(List.of("TRAINER_PM")));

        // The account is still ACTIVE — only token_version changed. A stale token must still fail.
        given().header("Authorization", "Bearer " + targetToken).when()
                .get("/api/v1/users/me").then().statusCode(401);

        Map<String, Object> userRow = jdbcTemplate.queryForMap(
                "SELECT status FROM users WHERE uuid = ?", targetUuid);
        assertThat(userRow.get("status")).isEqualTo("ACTIVE");
    }

    // --- plan update / cache eviction ---

    @Test
    void updatePlan_updatesRowAndCacheEvictedImmediately() {
        String adminToken = registerVerifyGrantRoleAndLogin("Admin Plan One", "ADMIN");
        Map<String, Object> original = jdbcTemplate.queryForMap("""
                SELECT id, name, price_inr, duration_days, max_projects, mentor_support, allows_batch,
                       allows_sprints, allows_pip, allows_internship_letter, allows_client_project, is_active
                  FROM subscription_plans WHERE code = 'CORPORATE_PROGRAM'
                """);
        long planId = ((Number) original.get("id")).longValue();

        try {
            // Populate the "plans" cache with the ORIGINAL name/price first.
            given().when().get("/api/v1/plans").then()
                    .statusCode(200)
                    .body("data.find { it.code == 'CORPORATE_PROGRAM' }.name", equalTo(original.get("name")));

            given()
                    .contentType(ContentType.JSON)
                    .header("Authorization", "Bearer " + adminToken)
                    .body(Map.ofEntries(
                            Map.entry("name", "Corporate Program TEST"),
                            Map.entry("priceInr", 39999.00),
                            Map.entry("durationDays", 200),
                            Map.entry("maxProjects", 5),
                            Map.entry("mentorSupport", true), Map.entry("allowsBatch", true), Map.entry("allowsSprints", true),
                            Map.entry("allowsPip", true), Map.entry("allowsInternshipLetter", true), Map.entry("allowsClientProject", true),
                            Map.entry("active", true)))
                .when()
                    .put("/api/v1/admin/plans/" + planId)
                .then()
                    .statusCode(200)
                    .body("data.name", equalTo("Corporate Program TEST"));

            // Immediately re-read GET /plans — no stale cache (build-plan.md: "cache evicted on write").
            given().when().get("/api/v1/plans").then()
                    .statusCode(200)
                    .body("data.find { it.code == 'CORPORATE_PROGRAM' }.name", equalTo("Corporate Program TEST"));
        } finally {
            restorePlan(adminToken, planId, original);
        }
    }

    // --- exports ---

    @Test
    void exportUsers_returnsPresignedUrlWithCorrectRowCount() {
        String adminToken = registerVerifyGrantRoleAndLogin("Admin Export One", "ADMIN");
        long usersBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .post("/api/v1/admin/exports/users")
            .then()
                .statusCode(200)
                .body("data.report", equalTo("USERS"))
                .body("data.rowCount", equalTo((int) usersBefore))
                .body("data.deliveredInline", equalTo(true))
                .body("data.downloadUrl", notNullValue());
    }

    @Test
    void exportUsersAsCsv_returnsPresignedCsvUrlWithCorrectRowCount() {
        String adminToken = registerVerifyGrantRoleAndLogin("Admin Export Csv", "ADMIN");
        long usersBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .post("/api/v1/admin/exports/users?format=csv")
            .then()
                .statusCode(200)
                .body("data.report", equalTo("USERS"))
                .body("data.format", equalTo("CSV"))
                .body("data.rowCount", equalTo((int) usersBefore))
                .body("data.downloadUrl", org.hamcrest.Matchers.containsString(".csv"));
    }

    @Test
    void exportUnsupportedFormat_returns400() {
        String adminToken = registerVerifyGrantRoleAndLogin("Admin Export Bad Format", "ADMIN");

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .post("/api/v1/admin/exports/users?format=pdf")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("EXPORT_FORMAT_NOT_SUPPORTED"));
    }

    @Test
    void exportUnsupportedReport_returns400() {
        String adminToken = registerVerifyGrantRoleAndLogin("Admin Export Bad", "ADMIN");

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .post("/api/v1/admin/exports/not-a-real-report")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("EXPORT_REPORT_NOT_SUPPORTED"));
    }

    // --- fixtures ---

    private void insertCapturedPayment(long userId, long planId, String currency, BigDecimal amount) {
        jdbcTemplate.update("""
                INSERT INTO payments (user_id, plan_id, gateway, gateway_order_id, amount, currency, status, captured_at)
                VALUES (?, ?, 'RAZORPAY', ?, ?, ?, 'CAPTURED', NOW(6))
                """, userId, planId, "order_" + UUID.randomUUID(), amount, currency);
    }

    private void restorePlan(String adminToken, long planId, Map<String, Object> original) {
        // A mutable map, not Map.of/Map.ofEntries — CORPORATE_PROGRAM's seeded max_projects is
        // NULL (V5__seed_plans.sql), and Map.of/Map.entry both reject null values outright.
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", original.get("name"));
        body.put("priceInr", original.get("price_inr"));
        body.put("durationDays", original.get("duration_days"));
        body.put("maxProjects", original.get("max_projects"));
        body.put("mentorSupport", original.get("mentor_support"));
        body.put("allowsBatch", original.get("allows_batch"));
        body.put("allowsSprints", original.get("allows_sprints"));
        body.put("allowsPip", original.get("allows_pip"));
        body.put("allowsInternshipLetter", original.get("allows_internship_letter"));
        body.put("allowsClientProject", original.get("allows_client_project"));
        body.put("active", original.get("is_active"));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(body)
            .when()
                .put("/api/v1/admin/plans/" + planId)
            .then()
                .statusCode(200);
    }

    private long currentUserId(String accessToken) {
        String uuid = given()
                .header("Authorization", "Bearer " + accessToken)
            .when()
                .get("/api/v1/users/me")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.uuid");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE uuid = ?", Long.class, uuid);
    }

    private String uuidOf(long userId) {
        return jdbcTemplate.queryForObject("SELECT uuid FROM users WHERE id = ?", String.class, userId);
    }

    private String registerVerifyAndLogin(String fullName) {
        return registerVerifyAndLogin(fullName, uniqueEmail(fullName));
    }

    private String registerVerifyAndLogin(String fullName, String email) {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery", "agreedToTerms", true))
            .when()
                .post("/api/v1/auth/register")
            .then()
                .statusCode(201);
        verifyEmailDirectly(email);
        return login(email);
    }

    private String registerVerifyGrantRoleAndLogin(String fullName, String roleCode) {
        String email = uniqueEmail(fullName);
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery", "agreedToTerms", true))
            .when()
                .post("/api/v1/auth/register")
            .then()
                .statusCode(201);
        verifyEmailDirectly(email);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);

        // Mandatory 2FA in this codebase is ADMIN/HR_MANAGER only (see BaClientFlowIT/
        // CertificateFlowIT's own identical branch) — CLIENT/TRAINER_PM log in normally.
        if ("ADMIN".equals(roleCode) || "HR_MANAGER".equals(roleCode)) {
            return completeMandatoryTwoFactorSetupAndLogin(email);
        }
        return login(email);
    }

    private String completeMandatoryTwoFactorSetupAndLogin(String email) {
        String challengeToken = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "correct horse battery"))
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(200)
                .body("data.twoFactorSetupRequired", equalTo(true))
                .extract().jsonPath().getString("data.challengeToken");

        String secretBase32 = given()
                .contentType(ContentType.JSON)
                .body(Map.of("challengeToken", challengeToken))
            .when()
                .post("/api/v1/auth/2fa/enable")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.secret");
        byte[] secret = Base32Codec.decode(secretBase32);

        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("challengeToken", challengeToken, "totpCode", currentTotpCode(secret)))
            .when()
                .post("/api/v1/auth/2fa/verify")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.tokens.accessToken");
    }

    private String currentTotpCode(byte[] secret) {
        long step = Instant.now().getEpochSecond() / 30;
        byte[] stepBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            stepBytes[i] = (byte) (step & 0xFF);
            step >>= 8;
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
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("Test TOTP computation failed", e);
        }
    }

    private void verifyEmailDirectly(String email) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        String raw = OpaqueTokenGenerator.generate();
        jdbcTemplate.update("""
                INSERT INTO email_verification_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, userId, OpaqueTokenGenerator.sha256Hex(raw), Instant.now().plus(1, ChronoUnit.HOURS));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", raw))
            .when()
                .post("/api/v1/auth/verify-email")
            .then()
                .statusCode(200);
    }

    private String login(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "correct horse battery"))
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.tokens.accessToken");
    }

    private String uniqueEmail(String fullName) {
        return fullName.toLowerCase().replace(" ", ".") + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }
}
