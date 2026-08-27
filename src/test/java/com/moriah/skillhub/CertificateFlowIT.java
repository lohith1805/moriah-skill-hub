package com.moriah.skillhub;

import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import com.moriah.skillhub.common.util.Base32Codec;
import io.restassured.RestAssured;
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

/**
 * build-plan.md feature 20's own "Verify" line, exercised end to end over real HTTP: "QR resolves
 * publicly with no auth. A student with an open PIP cannot be issued. A student never graduated
 * cannot be issued. Revoked reports invalid." Same structure as {@code PipFlowIT}/{@code
 * HrFlowIT}: real JWT auth for every authenticated call, fixture rows (batch/sprint/PIP/project/
 * task) inserted directly via JdbcTemplate rather than driving every prerequisite feature's own
 * full flow, and the mandatory-2FA helper for the one {@code ADMIN} login this class needs — every
 * {@code TRAINER_PM} login here does not need it, matching the actual mandatory-2FA role set this
 * project settled on ({@code ADMIN}/{@code HR_MANAGER} only — see {@code
 * registerVerifyGrantRoleAndLogin}'s own branch).
 */
class CertificateFlowIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void fullHappyPath_graduateIssueVerifyRevoke() {
        String pmToken = registerVerifyGrantRoleAndLogin("Cert Pm Happy", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Cert Student Happy");
        long studentId = currentUserId(studentToken);
        String studentUuid = uuidOf(studentId);
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ACTIVE");
        insertSprint(batchId, 1, "COMPLETED");

        given()
                .header("Authorization", "Bearer " + pmToken)
            .when()
                .post("/api/v1/batches/" + batchId + "/students/" + studentUuid + "/graduate")
            .then()
                .statusCode(200)
                .body("data.userUuid", equalTo(studentUuid))
                .body("data.graduatedAt", notNullValue());

        assertThat(batchStudentStatus(batchId, studentId)).isEqualTo("GRADUATED");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("batchId", batchId, "userUuid", studentUuid, "certificateType", "EXCELLENCE"))
            .when()
                .post("/api/v1/certificates/issue")
            .then()
                .statusCode(201)
                .body("data.certificateType", equalTo("EXCELLENCE"))
                .body("data.certificateNumber", org.hamcrest.Matchers.startsWith("MSH-CERT-"))
                .body("data.downloadUrl", notNullValue());

        String verificationCode = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("batchId", batchId, "userUuid", studentUuid))
            .when()
                .post("/api/v1/certificates/issue")
            .then()
                // Second issuance for the same graduated student is a separate certificate row —
                // this feature never spec's "one certificate per student" as a uniqueness rule, so
                // this just needs its own fresh verification code, not a rejection.
                .statusCode(201)
                .extract().jsonPath().getString("data.verificationCode");

        // No Authorization header at all — this is the public, unauthenticated verification path.
        given()
            .when()
                .get("/api/v1/certificates/verify/" + verificationCode)
            .then()
                .statusCode(200)
                .body("data.valid", equalTo(true))
                .body("data.holderFullName", equalTo("Cert Student Happy"))
                .body("data.revokedAt", nullValue());

        long secondCertificateId = jdbcTemplate.queryForObject(
                "SELECT id FROM certificates WHERE verification_code = ?", Long.class, verificationCode);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("reason", "Issued in error."))
            .when()
                .post("/api/v1/certificates/" + secondCertificateId + "/revoke")
            .then()
                .statusCode(200)
                .body("data.revokedAt", notNullValue())
                .body("data.revokeReason", equalTo("Issued in error."));

        given()
            .when()
                .get("/api/v1/certificates/verify/" + verificationCode)
            .then()
                .statusCode(200)
                .body("data.valid", equalTo(false))
                .body("data.revokedAt", notNullValue());

        // The first certificate is untouched by revoking the second.
        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .get("/api/v1/certificates/me")
            .then()
                .statusCode(200)
                .body("data.content.size()", equalTo(2));
    }

    @Test
    void issue_studentNeverGraduated_returns409() {
        String pmToken = registerVerifyGrantRoleAndLogin("Cert Pm NotGrad", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Cert Student NotGrad");
        long studentId = currentUserId(studentToken);
        String studentUuid = uuidOf(studentId);
        long batchId = insertBatch(pmId);
        // Deliberately no batch_students row at all — hasGraduatedFromBatch's own findByBatchIdAndUserId
        // lookup returns empty either way (no enrollment record vs. an enrolled-but-not-GRADUATED one),
        // so this covers the same eligibility-gate branch without leaving a permanently-ACTIVE row in
        // the shared Testcontainers database for MetricsRefreshFlowIT's later, whole-platform cohort
        // scan to pick up (that scan is deliberately unscoped — see BatchService.activeAndOnPipEnrollments's
        // own Javadoc — so any IT class leaving an ACTIVE/ON_PIP row lying around pollutes it).
        insertSprint(batchId, 1, "COMPLETED");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("batchId", batchId, "userUuid", studentUuid))
            .when()
                .post("/api/v1/certificates/issue")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void issue_openPipRecord_returns409() {
        String pmToken = registerVerifyGrantRoleAndLogin("Cert Pm OpenPip", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Cert Student OpenPip");
        long studentId = currentUserId(studentToken);
        String studentUuid = uuidOf(studentId);
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "GRADUATED");
        insertSprint(batchId, 1, "COMPLETED");
        insertOpenPipRecord(studentId, batchId);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("batchId", batchId, "userUuid", studentUuid))
            .when()
                .post("/api/v1/certificates/issue")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void issue_sprintNotCompleted_returns409() {
        String pmToken = registerVerifyGrantRoleAndLogin("Cert Pm SprintOpen", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Cert Student SprintOpen");
        long studentId = currentUserId(studentToken);
        String studentUuid = uuidOf(studentId);
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "GRADUATED");
        insertSprint(batchId, 1, "ACTIVE");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("batchId", batchId, "userUuid", studentUuid))
            .when()
                .post("/api/v1/certificates/issue")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void issue_batchWithNoSprintsAtAll_passesVacuously() {
        String pmToken = registerVerifyGrantRoleAndLogin("Cert Pm NoSprints", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Cert Student NoSprints");
        long studentId = currentUserId(studentToken);
        String studentUuid = uuidOf(studentId);
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "GRADUATED");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("batchId", batchId, "userUuid", studentUuid))
            .when()
                .post("/api/v1/certificates/issue")
            .then()
                .statusCode(201);
    }

    @Test
    void graduate_nonOwningPm_returns403() {
        String ownerPmToken = registerVerifyGrantRoleAndLogin("Cert Owner Pm", "TRAINER_PM");
        long ownerPmId = currentUserId(ownerPmToken);
        String otherPmToken = registerVerifyGrantRoleAndLogin("Cert Other Pm", "TRAINER_PM");
        String studentToken = registerVerifyAndLogin("Cert Student Grad403");
        long studentId = currentUserId(studentToken);
        String studentUuid = uuidOf(studentId);
        long batchId = insertBatch(ownerPmId);
        // No batch_students row needed — BatchService.graduate checks ownership (requireOwnerOrAdmin)
        // before it ever loads the batch_students row, so the 403 fires regardless of enrollment
        // state. Leaving this student unenrolled avoids a permanently-ACTIVE row in the shared
        // Testcontainers database (see issue_studentNeverGraduated_returns409's own comment above).

        given()
                .header("Authorization", "Bearer " + otherPmToken)
            .when()
                .post("/api/v1/batches/" + batchId + "/students/" + studentUuid + "/graduate")
            .then()
                .statusCode(403);
    }

    @Test
    void issue_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Cert Student Forbidden");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("batchId", 1, "userUuid", UUID.randomUUID().toString()))
            .when()
                .post("/api/v1/certificates/issue")
            .then()
                .statusCode(403);
    }

    @Test
    void revoke_nonOwningPm_returns403() {
        String ownerPmToken = registerVerifyGrantRoleAndLogin("Cert Revoke Owner Pm", "TRAINER_PM");
        long ownerPmId = currentUserId(ownerPmToken);
        String otherPmToken = registerVerifyGrantRoleAndLogin("Cert Revoke Other Pm", "TRAINER_PM");
        String studentToken = registerVerifyAndLogin("Cert Student Revoke403");
        long studentId = currentUserId(studentToken);
        String studentUuid = uuidOf(studentId);
        long batchId = insertBatch(ownerPmId);
        insertBatchStudent(batchId, studentId, "GRADUATED");
        insertSprint(batchId, 1, "COMPLETED");

        long certificateId = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerPmToken)
                .body(Map.of("batchId", batchId, "userUuid", studentUuid))
            .when()
                .post("/api/v1/certificates/issue")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + otherPmToken)
                .body(Map.of("reason", "Trying to revoke someone else's batch's certificate."))
            .when()
                .post("/api/v1/certificates/" + certificateId + "/revoke")
            .then()
                .statusCode(403);
    }

    @Test
    void verify_unknownCode_returns404() {
        given()
            .when()
                .get("/api/v1/certificates/verify/NOSUCHCODE01")
            .then()
                .statusCode(404)
                .body("error.code", equalTo("CERTIFICATE_NOT_FOUND"));
    }

    @Test
    void portfolio_reflectsIssuedCertificateAndCompletedProject() {
        String pmToken = registerVerifyGrantRoleAndLogin("Cert Pm Portfolio", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Cert Student Portfolio");
        long studentId = currentUserId(studentToken);
        String studentUuid = uuidOf(studentId);
        String slug = portfolioSlugOf(studentToken);

        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "GRADUATED");
        insertSprint(batchId, 1, "COMPLETED");
        long projectId = insertPublishedProject(pmId, "Portfolio Showcase Project");
        long sprintId = jdbcTemplate.queryForObject(
                "SELECT id FROM sprints WHERE batch_id = ? AND sprint_number = 1", Long.class, batchId);
        insertCompletedTaskForProject(sprintId, projectId, studentId);

        String verificationCode = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("batchId", batchId, "userUuid", studentUuid))
            .when()
                .post("/api/v1/certificates/issue")
            .then()
                .statusCode(201)
                .extract().jsonPath().getString("data.verificationCode");

        given()
            .when()
                .get("/api/v1/portfolio/" + slug)
            .then()
                .statusCode(200)
                .body("data.fullName", equalTo("Cert Student Portfolio"))
                .body("data.completedProjects.size()", equalTo(1))
                .body("data.completedProjects[0].title", equalTo("Portfolio Showcase Project"))
                .body("data.issuedCertificates.size()", equalTo(1))
                .body("data.issuedCertificates[0].verificationCode", equalTo(verificationCode));
    }

    // --- fixtures ---

    private long insertBatch(long pmId) {
        String trackCode = "CERTF-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO batches (name, track_code, pm_id, start_date, end_date, capacity, status)
                VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 90 DAY, 20, 'ACTIVE')
                """, "Batch-" + UUID.randomUUID(), trackCode, pmId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM batches WHERE track_code = ? AND pm_id = ?", Long.class, trackCode, pmId);
    }

    private void insertBatchStudent(long batchId, long userId, String status) {
        jdbcTemplate.update("INSERT INTO batch_students (batch_id, user_id, status) VALUES (?, ?, ?)",
                batchId, userId, status);
    }

    private void insertSprint(long batchId, int sprintNumber, String status) {
        jdbcTemplate.update("""
                INSERT INTO sprints (batch_id, sprint_number, start_date, end_date, status)
                VALUES (?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 14 DAY, ?)
                """, batchId, sprintNumber, status);
    }

    private void insertOpenPipRecord(long studentId, long batchId) {
        jdbcTemplate.update("""
                INSERT INTO pip_records
                    (user_id, batch_id, rule_code, trigger_reason, severity, triggered_at,
                     start_date, end_date, status, blocks_task_pull)
                VALUES (?, ?, 'ATTENDANCE_LOW', 'Test fixture trigger reason.', 'HIGH', NOW(6),
                        CURRENT_DATE, CURRENT_DATE + INTERVAL 15 DAY, 'TRIGGERED', false)
                """, studentId, batchId);
    }

    private long insertPublishedProject(long createdByUserId, String title) {
        String slug = "portfolio-showcase-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO projects (title, slug, status, created_by)
                VALUES (?, ?, 'PUBLISHED', ?)
                """, title, slug, createdByUserId);
        return jdbcTemplate.queryForObject("SELECT id FROM projects WHERE slug = ?", Long.class, slug);
    }

    private void insertCompletedTaskForProject(long sprintId, long projectId, long assignedToUserId) {
        jdbcTemplate.update("""
                INSERT INTO tasks (sprint_id, project_id, title, task_type, assigned_to, status, completed_at)
                VALUES (?, ?, ?, 'STORY', ?, 'COMPLETED', NOW(6))
                """, sprintId, projectId, "Task-" + UUID.randomUUID(), assignedToUserId);
    }

    private String batchStudentStatus(long batchId, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM batch_students WHERE batch_id = ? AND user_id = ?",
                String.class, batchId, userId);
    }

    private String portfolioSlugOf(String accessToken) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
            .when()
                .get("/api/v1/users/me")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.portfolioSlug");
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
        String email = uniqueEmail(fullName);
        given()
                .contentType("application/json")
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
                .contentType("application/json")
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery"))
            .when()
                .post("/api/v1/auth/register")
            .then()
                .statusCode(201);
        verifyEmailDirectly(email);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);

        // Mandatory 2FA in this codebase is ADMIN/HR_MANAGER only (see HrFlowIT/PipFlowIT's own
        // identical branch) — TRAINER_PM logs in normally.
        if ("ADMIN".equals(roleCode) || "HR_MANAGER".equals(roleCode)) {
            return completeMandatoryTwoFactorSetupAndLogin(email);
        }
        return login(email);
    }

    private String completeMandatoryTwoFactorSetupAndLogin(String email) {
        String challengeToken = given()
                .contentType("application/json")
                .body(Map.of("email", email, "password", "correct horse battery"))
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(200)
                .body("data.twoFactorSetupRequired", equalTo(true))
                .extract().jsonPath().getString("data.challengeToken");

        String secretBase32 = given()
                .contentType("application/json")
                .body(Map.of("challengeToken", challengeToken))
            .when()
                .post("/api/v1/auth/2fa/enable")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.secret");
        byte[] secret = Base32Codec.decode(secretBase32);

        return given()
                .contentType("application/json")
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
                .contentType("application/json")
                .body(Map.of("token", raw))
            .when()
                .post("/api/v1/auth/verify-email")
            .then()
                .statusCode(200);
    }

    private String login(String email) {
        return given()
                .contentType("application/json")
                .body(Map.of("email", email, "password", "correct horse battery"))
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.tokens.accessToken");
    }

    private String uniqueEmail(String fullName) {
        return fullName.toLowerCase().replace(" ", ".") + "-" + UUID.randomUUID() + "@example.com";
    }
}
