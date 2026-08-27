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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * build-plan.md feature 21's own "Verify" line, exercised end to end over real HTTP: "A client
 * requesting another client's project gets 403. Progress contains no student names or scores."
 * Plus the client-provisioning flow (build-plan.md: "CLIENT users are provisioned by ADMIN"),
 * requirement-document versioning/approval, and resource allocation. Same structure as {@code
 * CertificateFlowIT}/{@code HrFlowIT}: real JWT auth for every authenticated call, fixture rows
 * inserted directly via JdbcTemplate for cross-feature prerequisites (batches/sprints) this
 * feature doesn't own, and the mandatory-2FA helper for the one role that needs it in this
 * codebase — {@code ADMIN}/{@code HR_MANAGER} only (confirmed against {@code CertificateFlowIT}/
 * {@code HrFlowIT}'s own identical branch); {@code BUSINESS_ANALYST}, {@code CLIENT}, and
 * {@code TRAINER_PM} logins here never need it.
 */
class BaClientFlowIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // --- client provisioning ---

    @Test
    void clientProvisioning_fullFlow_createsUserLinksClientAndAllowsLogin() {
        String adminToken = registerVerifyGrantRoleAndLogin("Ba Admin One", "ADMIN");
        String contactEmail = uniqueEmail("Cli Contact One");

        Map<String, Object> response = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of(
                        "companyName", "Acme Corp",
                        "contactPerson", "Cli Contact One",
                        "email", contactEmail,
                        "phone", "9999999999",
                        "industry", "Retail",
                        "provisionPortalLogin", true))
            .when()
                .post("/api/v1/clients")
            .then()
                .statusCode(201)
                .body("data.companyName", equalTo("Acme Corp"))
                .body("data.status", equalTo("ACTIVE"))
                .body("data.userUuid", notNullValue())
                .extract().jsonPath().getMap("data");

        String userUuid = (String) response.get("userUuid");
        long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE uuid = ?", Long.class, userUuid);

        Map<String, Object> userRow = jdbcTemplate.queryForMap(
                "SELECT status, email_verified_at, password_hash FROM users WHERE id = ?", userId);
        assertThat(userRow.get("status")).isEqualTo("ACTIVE");
        assertThat(userRow.get("email_verified_at")).isNotNull();
        assertThat(userRow.get("password_hash")).isNull();

        Long linkedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM clients WHERE email = ?", Long.class, contactEmail);
        assertThat(linkedUserId).isEqualTo(userId);

        Integer resetTokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? AND used_at IS NULL AND expires_at > NOW(6)",
                Integer.class, userId);
        assertThat(resetTokenCount).isEqualTo(1);

        // Drive the actual set-password + login UX: mint our own raw token the same way
        // verifyEmailDirectly does for email verification (the real token is hashed and never
        // exposed by the API, by design), then reset and log in for real.
        String rawResetToken = OpaqueTokenGenerator.generate();
        jdbcTemplate.update("""
                INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, userId, OpaqueTokenGenerator.sha256Hex(rawResetToken), Instant.now().plus(1, ChronoUnit.HOURS));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", rawResetToken, "newPassword", "new client password"))
            .when()
                .post("/api/v1/auth/password/reset")
            .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", contactEmail, "password", "new client password"))
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(200)
                .body("data.tokens.accessToken", notNullValue());
    }

    @Test
    void createClient_withoutPortalLogin_leavesUserIdNull() {
        String adminToken = registerVerifyGrantRoleAndLogin("Ba Admin NoLogin", "ADMIN");
        String contactEmail = uniqueEmail("Cli NoLogin");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of(
                        "companyName", "NoLogin Inc",
                        "contactPerson", "Cli NoLogin",
                        "email", contactEmail,
                        "provisionPortalLogin", false))
            .when()
                .post("/api/v1/clients")
            .then()
                .statusCode(201)
                .body("data.userUuid", nullValue());

        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM clients WHERE email = ?", Long.class, contactEmail);
        assertThat(userId).isNull();
    }

    @Test
    void createClient_asNonAdmin_returns403() {
        String studentToken = registerVerifyAndLogin("Ba Stu Forbidden");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of(
                        "companyName", "Blocked Co",
                        "contactPerson", "Nobody",
                        "email", uniqueEmail("Blocked Co"),
                        "provisionPortalLogin", false))
            .when()
                .post("/api/v1/clients")
            .then()
                .statusCode(403);
    }

    @Test
    void createClient_duplicateEmailWithPortalLogin_returns409() {
        String adminToken = registerVerifyGrantRoleAndLogin("Ba Admin Dup", "ADMIN");
        String takenEmail = uniqueEmail("Already Registered");
        registerVerifyAndLogin("Already Registered", takenEmail);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of(
                        "companyName", "Dup Co",
                        "contactPerson", "Already Registered",
                        "email", takenEmail,
                        "provisionPortalLogin", true))
            .when()
                .post("/api/v1/clients")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("EMAIL_ALREADY_REGISTERED"));
    }

    // --- client project submission, requirement documents ---

    @Test
    void submitProjectDocumentApprove_fullFlow_versioningAndDoubleApprove() {
        String adminToken = registerVerifyGrantRoleAndLogin("Ba Admin Flow", "ADMIN");
        String clientToken = createClientPortalUserAndLogin(adminToken, "Flow Co", "Cli Flow");
        String baToken = registerVerifyGrantRoleAndLogin("Ba Analyst Flow", "BUSINESS_ANALYST");

        long projectId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + clientToken)
                .body(Map.of(
                        "title", "New Storefront",
                        "scopeDescription", "Build a storefront.",
                        "budgetRange", "10k-20k"))
            .when()
                .post("/api/v1/clients/projects")
            .then()
                .statusCode(201)
                .body("data.status", equalTo("SUBMITTED"))
                .extract().jsonPath().getLong("data.id");

        long firstDocumentId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + baToken)
                .body(Map.of(
                        "clientProjectId", projectId,
                        "docType", "BRD",
                        "title", "Business Requirements v1",
                        "content", "Initial scope."))
            .when()
                .post("/api/v1/ba/documents")
            .then()
                .statusCode(201)
                .body("data.status", equalTo("IN_REVIEW"))
                .body("data.version", equalTo(1))
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + baToken)
                .body(Map.of(
                        "clientProjectId", projectId,
                        "docType", "BRD",
                        "title", "Business Requirements v2",
                        "content", "Revised scope."))
            .when()
                .post("/api/v1/ba/documents")
            .then()
                .statusCode(201)
                .body("data.version", equalTo(2));

        given()
                .header("Authorization", "Bearer " + baToken)
            .when()
                .put("/api/v1/ba/documents/" + firstDocumentId + "/approve")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("APPROVED"))
                .body("data.approvedByUuid", notNullValue());

        given()
                .header("Authorization", "Bearer " + baToken)
            .when()
                .put("/api/v1/ba/documents/" + firstDocumentId + "/approve")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void createProject_asNonClient_returns403() {
        String baToken = registerVerifyGrantRoleAndLogin("Ba Analyst Forbidden", "BUSINESS_ANALYST");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + baToken)
                .body(Map.of("title", "T", "scopeDescription", "S"))
            .when()
                .post("/api/v1/clients/projects")
            .then()
                .statusCode(403);
    }

    @Test
    void createProject_clientUserWithNoLinkedClientRow_returns404() {
        String orphanToken = registerVerifyGrantRoleAndLogin("Ba Orphan Client", "CLIENT");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + orphanToken)
                .body(Map.of("title", "T", "scopeDescription", "S"))
            .when()
                .post("/api/v1/clients/projects")
            .then()
                .statusCode(404)
                .body("error.code", equalTo("CLIENT_NOT_FOUND"));
    }

    @Test
    void createDocument_asClient_returns403() {
        String adminToken = registerVerifyGrantRoleAndLogin("Ba Admin DocRole", "ADMIN");
        String clientToken = createClientPortalUserAndLogin(adminToken, "DocRole Co", "Cli DocRole");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + clientToken)
                .body(Map.of("clientProjectId", 1, "docType", "BRD", "title", "T", "content", "C"))
            .when()
                .post("/api/v1/ba/documents")
            .then()
                .statusCode(403);
    }

    @Test
    void approveDocument_asClient_returns403() {
        String clientToken = registerVerifyGrantRoleAndLogin("Ba Approve Client", "CLIENT");

        given()
                .header("Authorization", "Bearer " + clientToken)
            .when()
                .put("/api/v1/ba/documents/1/approve")
            .then()
                .statusCode(403);
    }

    // --- progress ---

    @Test
    void progress_ownerClientGets200_otherClientGets403_staffGetsAny() {
        String adminToken = registerVerifyGrantRoleAndLogin("Ba Admin Progress", "ADMIN");
        String ownerClientToken = createClientPortalUserAndLogin(adminToken, "Owner Co", "Cli Owner");
        String otherClientToken = createClientPortalUserAndLogin(adminToken, "Other Co", "Cli Other");
        String baToken = registerVerifyGrantRoleAndLogin("Ba Analyst Progress", "BUSINESS_ANALYST");
        String pmToken = registerVerifyGrantRoleAndLogin("Ba Pm Progress", "TRAINER_PM");
        long pmId = currentUserId(pmToken);

        long projectId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + ownerClientToken)
                .body(Map.of("title", "Progress Project", "scopeDescription", "S"))
            .when()
                .post("/api/v1/clients/projects")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        // No batch allocated yet — zeroed shape, not an error (build-plan.md feature 21 decision).
        given()
                .header("Authorization", "Bearer " + ownerClientToken)
            .when()
                .get("/api/v1/clients/projects/" + projectId + "/progress")
            .then()
                .statusCode(200)
                .body("data.targetBatchId", nullValue())
                .body("data.milestoneCompletionFraction", equalTo(0.0f))
                .body("data.burndown.size()", equalTo(0));

        long batchId = insertBatch(pmId);
        insertSprint(batchId, 1, "COMPLETED", 10, 10);
        insertSprint(batchId, 2, "ACTIVE", 8, 3);
        jdbcTemplate.update("UPDATE client_projects SET target_batch_id = ? WHERE id = ?", batchId, projectId);

        given()
                .header("Authorization", "Bearer " + ownerClientToken)
            .when()
                .get("/api/v1/clients/projects/" + projectId + "/progress")
            .then()
                .statusCode(200)
                .body("data.targetBatchId", equalTo((int) batchId))
                .body("data.milestoneCompletionFraction", equalTo(0.5f))
                .body("data.burndown.size()", equalTo(2))
                .body("data.burndown[0].sprintStatus", equalTo("COMPLETED"))
                .body("data.burndown[0].plannedPoints", equalTo(10))
                .body("data.burndown[0].completedPoints", equalTo(10))
                .body("data.burndown[1].sprintStatus", equalTo("ACTIVE"))
                .body("data.burndown[1].completedPoints", equalTo(3));

        given()
                .header("Authorization", "Bearer " + otherClientToken)
            .when()
                .get("/api/v1/clients/projects/" + projectId + "/progress")
            .then()
                .statusCode(403);

        given()
                .header("Authorization", "Bearer " + baToken)
            .when()
                .get("/api/v1/clients/projects/" + projectId + "/progress")
            .then()
                .statusCode(200)
                .body("data.milestoneCompletionFraction", equalTo(0.5f));

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/v1/clients/projects/" + projectId + "/progress")
            .then()
                .statusCode(200);
    }

    @Test
    void progress_wrongRoleEntirely_returns403() {
        String pmToken = registerVerifyGrantRoleAndLogin("Ba Pm WrongRole", "TRAINER_PM");

        given()
                .header("Authorization", "Bearer " + pmToken)
            .when()
                .get("/api/v1/clients/projects/1/progress")
            .then()
                .statusCode(403);
    }

    // --- resource allocations ---

    @Test
    void createAllocation_asBusinessAnalyst_returns201() {
        String adminToken = registerVerifyGrantRoleAndLogin("Ba Admin Alloc", "ADMIN");
        String clientToken = createClientPortalUserAndLogin(adminToken, "Alloc Co", "Cli Alloc");
        String baToken = registerVerifyGrantRoleAndLogin("Ba Analyst Alloc", "BUSINESS_ANALYST");
        String pmToken = registerVerifyGrantRoleAndLogin("Ba Pm Alloc", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Ba Student Alloc");
        String studentUuid = uuidOf(currentUserId(studentToken));
        long batchId = insertBatch(pmId);

        long projectId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + clientToken)
                .body(Map.of("title", "Alloc Project", "scopeDescription", "S"))
            .when()
                .post("/api/v1/clients/projects")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + baToken)
                .body(Map.of(
                        "clientProjectId", projectId,
                        "batchId", batchId,
                        "userUuid", studentUuid,
                        "roleInProject", "Developer",
                        "allocatedDays", 20,
                        "storyPointsEstimate", 13,
                        "fromDate", "2026-09-01",
                        "toDate", "2026-09-30"))
            .when()
                .post("/api/v1/ba/allocations")
            .then()
                .statusCode(201)
                .body("data.userUuid", equalTo(studentUuid))
                .body("data.roleInProject", equalTo("Developer"))
                .body("data.allocatedDays", equalTo(20));
    }

    @Test
    void createAllocation_asClient_returns403() {
        String adminToken = registerVerifyGrantRoleAndLogin("Ba Admin AllocRole", "ADMIN");
        String clientToken = createClientPortalUserAndLogin(adminToken, "AllocRole Co", "Cli AllocRole");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + clientToken)
                .body(Map.of(
                        "clientProjectId", 1, "batchId", 1, "userUuid", UUID.randomUUID().toString(),
                        "roleInProject", "Developer", "allocatedDays", 5,
                        "fromDate", "2026-09-01", "toDate", "2026-09-05"))
            .when()
                .post("/api/v1/ba/allocations")
            .then()
                .statusCode(403);
    }

    @Test
    void createAllocation_nonexistentBatch_returns404() {
        String adminToken = registerVerifyGrantRoleAndLogin("Ba Admin AllocBad", "ADMIN");
        String clientToken = createClientPortalUserAndLogin(adminToken, "AllocBad Co", "Cli AllocBad");
        String baToken = registerVerifyGrantRoleAndLogin("Ba Analyst AllocBad", "BUSINESS_ANALYST");
        String studentToken = registerVerifyAndLogin("Ba Student AllocBad");
        String studentUuid = uuidOf(currentUserId(studentToken));

        long projectId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + clientToken)
                .body(Map.of("title", "Bad Alloc Project", "scopeDescription", "S"))
            .when()
                .post("/api/v1/clients/projects")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + baToken)
                .body(Map.of(
                        "clientProjectId", projectId,
                        "batchId", 999999,
                        "userUuid", studentUuid,
                        "roleInProject", "Developer",
                        "allocatedDays", 5,
                        "fromDate", "2026-09-01",
                        "toDate", "2026-09-05"))
            .when()
                .post("/api/v1/ba/allocations")
            .then()
                .statusCode(404)
                .body("error.code", equalTo("BATCH_NOT_FOUND"));
    }

    // --- fixtures ---

    private long insertBatch(long pmId) {
        String trackCode = "BACL-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO batches (name, track_code, pm_id, start_date, end_date, capacity, status)
                VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 90 DAY, 20, 'ACTIVE')
                """, "Batch-" + UUID.randomUUID(), trackCode, pmId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM batches WHERE track_code = ? AND pm_id = ?", Long.class, trackCode, pmId);
    }

    private void insertSprint(long batchId, int sprintNumber, String status, int plannedPoints, int completedPoints) {
        jdbcTemplate.update("""
                INSERT INTO sprints (batch_id, sprint_number, start_date, end_date, status, planned_points, completed_points)
                VALUES (?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 14 DAY, ?, ?, ?)
                """, batchId, sprintNumber, status, plannedPoints, completedPoints);
    }

    /** Provisions a CLIENT portal user through the real {@code POST /api/v1/clients} endpoint
     * (same as production), then completes the password-reset UX with a self-minted token (the
     * real one is hashed and never exposed by the API — see
     * {@code clientProvisioning_fullFlow_createsUserLinksClientAndAllowsLogin} for the isolated
     * version of this same trick) and logs in, returning the resulting access token. */
    private String createClientPortalUserAndLogin(String adminToken, String companyName, String contactPerson) {
        String email = uniqueEmail(contactPerson);
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of(
                        "companyName", companyName,
                        "contactPerson", contactPerson,
                        "email", email,
                        "provisionPortalLogin", true))
            .when()
                .post("/api/v1/clients")
            .then()
                .statusCode(201);

        long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        String rawResetToken = OpaqueTokenGenerator.generate();
        jdbcTemplate.update("""
                INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, userId, OpaqueTokenGenerator.sha256Hex(rawResetToken), Instant.now().plus(1, ChronoUnit.HOURS));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", rawResetToken, "newPassword", "client portal password"))
            .when()
                .post("/api/v1/auth/password/reset")
            .then()
                .statusCode(200);

        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "client portal password"))
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.tokens.accessToken");
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
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery"))
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
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery"))
            .when()
                .post("/api/v1/auth/register")
            .then()
                .statusCode(201);
        verifyEmailDirectly(email);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);

        // Mandatory 2FA in this codebase is ADMIN/HR_MANAGER only (see CertificateFlowIT/HrFlowIT's
        // own identical branch) — BUSINESS_ANALYST/CLIENT/TRAINER_PM log in normally.
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
