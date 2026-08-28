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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * build-plan.md feature 17 verify line, exercised end to end over real HTTP: "A fixture per rule
 * sitting just above and just below the threshold triggers exactly as expected... A second run
 * creates no duplicate. Clearing at 70% completion is rejected." Real HTTP + JWT auth throughout
 * (same reasoning as {@code ProjectFlowIT}/{@code BatchFlowIT}: this needs the real {@code
 * @PreAuthorize}/ownership/state-machine stack, not mocks) — pip_records/pip_milestones/
 * student_metrics fixture rows are inserted directly via JdbcTemplate rather than by running
 * {@code PipEvaluationService} first, since {@link PipEvaluationFlowIT} already covers the
 * trigger mechanism itself; this class only needs an already-triggered record to exercise the
 * human-driven endpoints and {@code TaskPullGuard}'s cross-package integration against.
 */
class PipFlowIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void me_asStudent_returnsOwnOpenRecord() {
        String pmToken = registerVerifyGrantRoleAndLogin("Pip Pm Me", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Pip Student Me");
        long studentId = currentUserId(studentToken);
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ON_PIP");
        long recordId = insertPipRecord(studentId, batchId, "ATTENDANCE_LOW", "TRIGGERED", false);
        insertMilestone(recordId, "PENDING");

        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .get("/api/v1/pip/me")
            .then()
                .statusCode(200)
                .body("data.id", equalTo((int) recordId))
                .body("data.ruleCode", equalTo("ATTENDANCE_LOW"))
                .body("data.milestones.size()", equalTo(1));
    }

    @Test
    void me_noOpenRecord_returns404() {
        String studentToken = registerVerifyAndLogin("Pip Student Clean");

        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .get("/api/v1/pip/me")
            .then()
                .statusCode(404);
    }

    @Test
    void review_asNonOwningPm_returns403() {
        String ownerPmToken = registerVerifyGrantRoleAndLogin("Pip Owner Pm", "TRAINER_PM");
        long ownerPmId = currentUserId(ownerPmToken);
        String otherPmToken = registerVerifyGrantRoleAndLogin("Pip Other Pm", "TRAINER_PM");
        String studentToken = registerVerifyAndLogin("Pip Student Forbidden");
        long studentId = currentUserId(studentToken);
        long batchId = insertBatch(ownerPmId);
        insertBatchStudent(batchId, studentId, "ON_PIP");
        long recordId = insertPipRecord(studentId, batchId, "ATTENDANCE_LOW", "TRIGGERED", false);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + otherPmToken)
                .body(Map.of("outcome", "CLEARED", "reviewNotes", "Looks fine."))
            .when()
                .post("/api/v1/pip/" + recordId + "/review")
            .then()
                .statusCode(403);
    }

    @Test
    void review_clearedBelowTaskCompletionThreshold_returns409() {
        String pmToken = registerVerifyGrantRoleAndLogin("Pip Pm Reject", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Pip Student Reject");
        long studentId = currentUserId(studentToken);
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ON_PIP");
        long recordId = insertPipRecord(studentId, batchId, "ATTENDANCE_LOW", "IN_PROGRESS", false);
        insertStudentMetric(studentId, batchId, "70.00", 0);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("outcome", "CLEARED", "reviewNotes", "Trying to clear early."))
            .when()
                .post("/api/v1/pip/" + recordId + "/review")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("PIP_CLEARANCE_CRITERIA_NOT_MET"));
    }

    @Test
    void review_clearedMeetingCriteria_returns200AndReactivatesStudent() {
        String pmToken = registerVerifyGrantRoleAndLogin("Pip Pm Clear", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Pip Student Clear");
        long studentId = currentUserId(studentToken);
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ON_PIP");
        long recordId = insertPipRecord(studentId, batchId, "ATTENDANCE_LOW", "IN_PROGRESS", false);
        insertStudentMetric(studentId, batchId, "90.00", 0);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("outcome", "CLEARED", "reviewNotes", "Fully turned around."))
            .when()
                .post("/api/v1/pip/" + recordId + "/review")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("CLEARED"));

        assertThat(batchStudentStatus(batchId, studentId)).isEqualTo("ACTIVE");
    }

    @Test
    void completeMilestone_asOwningPm_advancesRecordToInProgress() {
        String pmToken = registerVerifyGrantRoleAndLogin("Pip Pm Milestone", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Pip Student Milestone");
        long studentId = currentUserId(studentToken);
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ON_PIP");
        long recordId = insertPipRecord(studentId, batchId, "ATTENDANCE_LOW", "TRIGGERED", false);
        long milestoneId = insertMilestone(recordId, "PENDING");

        given()
                .header("Authorization", "Bearer " + pmToken)
            .when()
                .post("/api/v1/pip/" + recordId + "/milestones/" + milestoneId + "/complete")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("COMPLETED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM pip_records WHERE id = ?", String.class, recordId))
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void pull_blockedByOpenProjectDelayPip_returns409() {
        String pmToken = registerVerifyGrantRoleAndLogin("Pip Pm Pull", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Pip Student Pull");
        long studentId = currentUserId(studentToken);
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ON_PIP");
        insertPipRecord(studentId, batchId, "PROJECT_DELAY", "TRIGGERED", true);
        long sprintId = insertSprint(batchId);
        long taskId = insertTask(sprintId);

        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/tasks/" + taskId + "/pull")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("TASK_PULL_BLOCKED_BY_PIP"));
    }

    @Test
    void pull_openPipNotBlockingType_stillSucceeds() {
        String pmToken = registerVerifyGrantRoleAndLogin("Pip Pm Pull Ok", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String studentToken = registerVerifyAndLogin("Pip Student Pull Ok");
        long studentId = currentUserId(studentToken);
        long batchId = insertBatch(pmId);
        insertBatchStudent(batchId, studentId, "ON_PIP");
        insertPipRecord(studentId, batchId, "ATTENDANCE_LOW", "TRIGGERED", false);
        long sprintId = insertSprint(batchId);
        long taskId = insertTask(sprintId);

        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/tasks/" + taskId + "/pull")
            .then()
                .statusCode(200);
    }

    @Test
    void updateRule_asAdmin_updatesThreshold() {
        String adminToken = registerVerifyGrantRoleAndLogin("Pip Rule Admin", "ADMIN");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("thresholdValue", 80.00, "windowDays", 14, "severity", "HIGH", "active", true))
            .when()
                .put("/api/v1/pip/rules/ATTENDANCE_LOW")
            .then()
                .statusCode(200)
                .body("data.thresholdValue", equalTo(80.00f));

        // Restore the default so other tests in this class (and MetricsRefreshFlowIT-adjacent
        // fixtures elsewhere in the same shared container) aren't affected by this mutation.
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("thresholdValue", 75.00, "windowDays", 14, "severity", "HIGH", "active", true))
            .when()
                .put("/api/v1/pip/rules/ATTENDANCE_LOW")
            .then()
                .statusCode(200);
    }

    @Test
    void updateRule_asPm_returns403() {
        String pmToken = registerVerifyGrantRoleAndLogin("Pip Rule Pm", "TRAINER_PM");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("thresholdValue", 80.00, "windowDays", 14, "severity", "HIGH", "active", true))
            .when()
                .put("/api/v1/pip/rules/ATTENDANCE_LOW")
            .then()
                .statusCode(403);
    }

    // ---------------------------------------------------------------------------------------
    // PipController.list / rules — feature 24 coverage-audit gap: neither GET endpoint had any
    // test coverage at all before this (not even a happy path), and the {TRAINER_PM, HR_MANAGER,
    // ADMIN} role group they share had no 403 test anywhere in the suite.
    // ---------------------------------------------------------------------------------------

    @Test
    void list_asTrainerPm_returnsPipRecordsForBatch() {
        String pmToken = registerVerifyGrantRoleAndLogin("Pip List Pm", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        long batchId = insertBatch(pmId);
        String studentToken = registerVerifyAndLogin("Pip List Student");
        long studentId = currentUserId(studentToken);
        insertBatchStudent(batchId, studentId, "ON_PIP");
        insertPipRecord(studentId, batchId, "ATTENDANCE_LOW", "TRIGGERED", false);

        given()
                .header("Authorization", "Bearer " + pmToken)
            .when()
                .get("/api/v1/pip?batchId=" + batchId)
            .then()
                .statusCode(200)
                .body("data.content.size()", greaterThanOrEqualTo(1))
                .body("data.content[0].ruleCode", equalTo("ATTENDANCE_LOW"));
    }

    @Test
    void list_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Pip List Wrong Role");

        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .get("/api/v1/pip")
            .then()
                .statusCode(403);
    }

    @Test
    void rules_asHrManager_returnsAllSixRules() {
        String hrToken = registerVerifyGrantRoleAndLogin("Pip Rules Hr", "HR_MANAGER");

        given()
                .header("Authorization", "Bearer " + hrToken)
            .when()
                .get("/api/v1/pip/rules")
            .then()
                .statusCode(200)
                .body("data.size()", equalTo(6));
    }

    @Test
    void rules_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Pip Rules Wrong Role");

        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .get("/api/v1/pip/rules")
            .then()
                .statusCode(403);
    }

    private long insertBatch(long pmId) {
        String trackCode = "PIPF-" + UUID.randomUUID().toString().substring(0, 8);
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

    private long insertSprint(long batchId) {
        jdbcTemplate.update("""
                INSERT INTO sprints (batch_id, sprint_number, start_date, end_date, status)
                VALUES (?, 1, CURRENT_DATE, CURRENT_DATE + INTERVAL 14 DAY, 'ACTIVE')
                """, batchId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sprints WHERE batch_id = ? AND sprint_number = 1", Long.class, batchId);
    }

    private long insertTask(long sprintId) {
        jdbcTemplate.update("""
                INSERT INTO tasks (sprint_id, title, task_type, status)
                VALUES (?, ?, 'STORY', 'BACKLOG')
                """, sprintId, "Task-" + UUID.randomUUID());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tasks WHERE sprint_id = ? ORDER BY id DESC LIMIT 1", Long.class, sprintId);
    }

    private long insertPipRecord(long studentId, long batchId, String ruleCode, String status, boolean blocksTaskPull) {
        jdbcTemplate.update("""
                INSERT INTO pip_records
                    (user_id, batch_id, rule_code, trigger_reason, severity, triggered_at,
                     start_date, end_date, status, blocks_task_pull)
                VALUES (?, ?, ?, 'Test fixture trigger reason.', 'HIGH', NOW(6),
                        CURRENT_DATE, CURRENT_DATE + INTERVAL 15 DAY, ?, ?)
                """, studentId, batchId, ruleCode, status, blocksTaskPull);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM pip_records WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, studentId);
    }

    private long insertMilestone(long pipRecordId, String status) {
        jdbcTemplate.update("""
                INSERT INTO pip_milestones (pip_record_id, title, due_date, status)
                VALUES (?, 'Attend every standup', CURRENT_DATE + INTERVAL 10 DAY, ?)
                """, pipRecordId, status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM pip_milestones WHERE pip_record_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, pipRecordId);
    }

    private void insertStudentMetric(long userId, long batchId, String taskCompletionPercent, int unsatisfactoryReviews) {
        jdbcTemplate.update("""
                INSERT INTO student_metrics
                    (user_id, batch_id, computed_at, attendance_present, attendance_total,
                     tasks_assigned, tasks_completed, tasks_overdue_48h, task_completion_percent,
                     quiz_attempts_count, consecutive_assignments_missed, unsatisfactory_reviews)
                VALUES (?, ?, NOW(6), 0, 0, 0, 0, 0, ?, 0, 0, ?)
                """, userId, batchId, taskCompletionPercent, unsatisfactoryReviews);
    }

    private String batchStudentStatus(long batchId, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM batch_students WHERE batch_id = ? AND user_id = ?",
                String.class, batchId, userId);
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
