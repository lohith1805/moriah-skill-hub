package com.moriah.skillhub;

import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * build-plan.md feature 13 verify line: "Check-in after cutoff records LATE. Double check-in
 * creates one row." {@code AttendanceFinalisationJob}'s own verify criteria ("A student who never
 * checks in has an ABSENT row the next morning ... re-running does not double-write") are covered
 * by {@code AttendanceFinalisationJobIT} instead — this file is the check-in/override HTTP surface.
 * Real HTTP + JWT auth throughout (same reasoning as {@code SprintTaskFlowIT}) — can't use
 * {@code @Transactional} rollback, so every test tracks and cleans up its own rows.
 */
class AttendanceFlowIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> insertedBatchIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @AfterEach
    void cleanUp() {
        if (!insertedBatchIds.isEmpty()) {
            String placeholders = placeholders(insertedBatchIds.size());
            jdbcTemplate.update("""
                    DELETE a FROM attendance a JOIN standups s ON s.id = a.standup_id
                     WHERE s.batch_id IN (%s)
                    """.formatted(placeholders), insertedBatchIds.toArray());
            jdbcTemplate.update("DELETE FROM standups WHERE batch_id IN (%s)".formatted(placeholders),
                    insertedBatchIds.toArray());
            jdbcTemplate.update("DELETE FROM batch_students WHERE batch_id IN (%s)".formatted(placeholders),
                    insertedBatchIds.toArray());
            jdbcTemplate.update("DELETE FROM batches WHERE id IN (%s)".formatted(placeholders),
                    insertedBatchIds.toArray());
        }
        insertedBatchIds.clear();
    }

    // ---------------------------------------------------------------------------------------
    // Role gates
    // ---------------------------------------------------------------------------------------

    @Test
    void createStandup_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Standup Bystander");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("batchId", 1, "scheduledAt", Instant.now().toString()))
            .when()
                .post("/api/v1/standups")
            .then()
                .statusCode(403);
    }

    @Test
    void checkin_asTrainerPm_returns403() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Checkin Role PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("ROLE_TRACK", pmUserId);
        long standupId = insertStandup(batchId, Instant.now(), 15);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of())
            .when()
                .post("/api/v1/standups/" + standupId + "/checkin")
            .then()
                .statusCode(403);
    }

    @Test
    void override_asStudent_returns403() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Override Role PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("ROLE_TRACK2", pmUserId);
        long standupId = insertStandup(batchId, Instant.now(), 15);
        String studentToken = registerVerifyAndLogin("Override Bystander");
        String studentUuid = uuidOf(studentToken);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("userUuid", studentUuid, "status", "PRESENT"))
            .when()
                .post("/api/v1/standups/" + standupId + "/attendance")
            .then()
                .statusCode(403);
    }

    // ---------------------------------------------------------------------------------------
    // StandupController.list — feature 24 coverage-audit gap: GET /api/v1/standups had zero test
    // coverage anywhere in the suite (not even a happy path), and the {TRAINER_PM, ADMIN, STUDENT}
    // role group it shares with several other list endpoints (AssessmentController.list,
    // SprintController.list, TaskController.list) had no 403 test proving a genuinely excluded
    // role (e.g. CLIENT, HR_MANAGER, BUSINESS_ANALYST, LEAD_GEN, DEVELOPER) is rejected.
    // ---------------------------------------------------------------------------------------

    @Test
    void list_asTrainerPm_returnsStandupsForBatch() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Standup List PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("LIST_TRACK", pmUserId);
        insertStandup(batchId, Instant.now(), 15);

        given()
                .header("Authorization", "Bearer " + pmToken)
            .when()
                .get("/api/v1/standups?batchId=" + batchId)
            .then()
                .statusCode(200)
                .body("data.content.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
    }

    @Test
    void list_asClient_returns403() {
        String clientOnlyToken = registerAndLoginWithOnlyRole("Standup List Client", "CLIENT");

        given()
                .header("Authorization", "Bearer " + clientOnlyToken)
            .when()
                .get("/api/v1/standups?batchId=1")
            .then()
                .statusCode(403);
    }

    // ---------------------------------------------------------------------------------------
    // Check-in
    // ---------------------------------------------------------------------------------------

    @Test
    void checkin_beforeCutoff_recordsPresentAndAppearsInMe() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Present PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("PRESENT_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Present Student");
        long studentUserId = currentUserId(studentToken);
        insertActiveMember(batchId, studentUserId);
        long standupId = insertStandup(batchId, Instant.now().minus(2, ChronoUnit.MINUTES), 15);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of())
            .when()
                .post("/api/v1/standups/" + standupId + "/checkin")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("PRESENT"));

        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .get("/api/v1/attendance/me")
            .then()
                .statusCode(200)
                .body("data.content[0].status", equalTo("PRESENT"));
    }

    @Test
    void checkin_afterCutoff_recordsLate() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Late PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("LATE_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Late Student");
        long studentUserId = currentUserId(studentToken);
        insertActiveMember(batchId, studentUserId);
        // Scheduled 30 minutes ago with a 15-minute cutoff — checking in now is well past it.
        long standupId = insertStandup(batchId, Instant.now().minus(30, ChronoUnit.MINUTES), 15);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("blockerNotes", "overslept"))
            .when()
                .post("/api/v1/standups/" + standupId + "/checkin")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("LATE"))
                .body("data.blockerNotes", equalTo("overslept"));
    }

    @Test
    void checkin_notActiveBatchMember_returns403() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("NonMember PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("NONMEMBER_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Outsider Student");
        long standupId = insertStandup(batchId, Instant.now(), 15);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of())
            .when()
                .post("/api/v1/standups/" + standupId + "/checkin")
            .then()
                .statusCode(403)
                .body("error.code", equalTo("NOT_BATCH_MEMBER"));
    }

    @Test
    void checkin_concurrentDoublePost_createsExactlyOneRow() throws Exception {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Concurrent PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("CONCURRENT_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Concurrent Student");
        long studentUserId = currentUserId(studentToken);
        insertActiveMember(batchId, studentUserId);
        long standupId = insertStandup(batchId, Instant.now(), 15);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Callable<Integer> checkin = () -> given()
                    .contentType("application/json")
                    .header("Authorization", "Bearer " + studentToken)
                    .body(Map.of())
                .when()
                    .post("/api/v1/standups/" + standupId + "/checkin")
                .then()
                    .extract().statusCode();

            List<Future<Integer>> futures = executor.invokeAll(List.of(checkin, checkin));
            for (Future<Integer> future : futures) {
                assertThat(future.get()).isEqualTo(200);
            }
        } finally {
            executor.shutdown();
        }

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance WHERE standup_id = ? AND user_id = ?",
                Long.class, standupId, studentUserId);
        assertThat(rowCount).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------
    // PM override
    // ---------------------------------------------------------------------------------------

    @Test
    void override_studentNeverCheckedIn_createsRowAndAudits() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Override Create PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("OVERRIDE_CREATE_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Override Create Student");
        long studentUserId = currentUserId(studentToken);
        String studentUuid = uuidOf(studentToken);
        insertActiveMember(batchId, studentUserId);
        long standupId = insertStandup(batchId, Instant.now().minus(1, ChronoUnit.HOURS), 15);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("userUuid", studentUuid, "status", "EXCUSED", "blockerNotes", "medical"))
            .when()
                .post("/api/v1/standups/" + standupId + "/attendance")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("EXCUSED"))
                .body("data.autoMarked", equalTo(false));

        Long auditCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_logs WHERE action = 'ATTENDANCE_OVERRIDE' AND entity_type = 'Attendance'
                """, Long.class);
        assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void override_existingCheckin_updatesStatus() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Override Update PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("OVERRIDE_UPDATE_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Override Update Student");
        long studentUserId = currentUserId(studentToken);
        String studentUuid = uuidOf(studentToken);
        insertActiveMember(batchId, studentUserId);
        long standupId = insertStandup(batchId, Instant.now().minus(1, ChronoUnit.MINUTES), 15);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of())
            .when()
                .post("/api/v1/standups/" + standupId + "/checkin")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("PRESENT"));

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("userUuid", studentUuid, "status", "ABSENT"))
            .when()
                .post("/api/v1/standups/" + standupId + "/attendance")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("ABSENT"));

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance WHERE standup_id = ? AND user_id = ?",
                Long.class, standupId, studentUserId);
        assertThat(rowCount).isEqualTo(1);
    }

    /** `/review` regression: the concurrent-double-insert recovery read ({@code
     * AttendanceWriter.findExisting}) must eagerly fetch {@code markedBy}, not just {@code
     * user}/{@code standup} — otherwise recovering a row a PM already set (non-null {@code
     * markedBy}) throws {@code LazyInitializationException} instead of returning it, turning a
     * should-be-idempotent 200 into a 500. */
    @Test
    void checkin_afterPmAlreadyOverrode_recoversRowInstead500s() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("PreOverride PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("PRE_OVERRIDE_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("PreOverride Student");
        long studentUserId = currentUserId(studentToken);
        String studentUuid = uuidOf(studentToken);
        insertActiveMember(batchId, studentUserId);
        long standupId = insertStandup(batchId, Instant.now().minus(1, ChronoUnit.MINUTES), 15);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("userUuid", studentUuid, "status", "EXCUSED"))
            .when()
                .post("/api/v1/standups/" + standupId + "/attendance")
            .then()
                .statusCode(200);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of())
            .when()
                .post("/api/v1/standups/" + standupId + "/checkin")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("EXCUSED"));
    }

    @Test
    void override_cancelledStandup_returns409() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Override Cancelled PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("OVERRIDE_CANCEL_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Override Cancel Student");
        String studentUuid = uuidOf(studentToken);
        long standupId = insertStandup(batchId, Instant.now(), 15);
        jdbcTemplate.update("UPDATE standups SET status = 'CANCELLED' WHERE id = ?", standupId);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("userUuid", studentUuid, "status", "PRESENT"))
            .when()
                .post("/api/v1/standups/" + standupId + "/attendance")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("STANDUP_CANCELLED"));
    }

    // ---------------------------------------------------------------------------------------
    // Scheduling
    // ---------------------------------------------------------------------------------------

    @Test
    void createStandup_thenCancel_succeeds() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Cancel PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("CANCEL_TRACK", pmUserId);

        long standupId = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("batchId", batchId, "scheduledAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                        "lateCutoffMinutes", 20))
            .when()
                .post("/api/v1/standups")
            .then()
                .statusCode(201)
                .body("data.status", equalTo("SCHEDULED"))
                .body("data.lateCutoffMinutes", equalTo(20))
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("status", "CANCELLED"))
            .when()
                .put("/api/v1/standups/" + standupId)
            .then()
                .statusCode(200)
                .body("data.status", equalTo("CANCELLED"));
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private long insertBatch(String trackCode, long pmUserId) {
        jdbcTemplate.update("""
                INSERT INTO batches (name, track_code, pm_id, start_date, end_date, capacity, status)
                VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 90 DAY, 20, 'ACTIVE')
                """, "Batch-" + UUID.randomUUID(), trackCode, pmUserId);
        long batchId = jdbcTemplate.queryForObject(
                "SELECT id FROM batches WHERE track_code = ? AND pm_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, trackCode, pmUserId);
        insertedBatchIds.add(batchId);
        return batchId;
    }

    private void insertActiveMember(long batchId, long userId) {
        jdbcTemplate.update("INSERT INTO batch_students (batch_id, user_id) VALUES (?, ?)", batchId, userId);
    }

    /** {@code Timestamp.valueOf(LocalDateTime.ofInstant(instant, UTC))}, not a raw {@code Instant}
     * bind parameter — see {@code AttendanceFinalisationJobIT#insertStandup}'s Javadoc: confirmed
     * the hard way that the MySQL driver converts a raw {@code java.time.Instant} using the JVM's
     * default timezone (IST on this sandbox) before writing the timezone-naive {@code
     * DATETIME(6)} column, a systematic 5.5-hour offset from what Hibernate itself later reads. */
    private long insertStandup(long batchId, Instant scheduledAt, int lateCutoffMinutes) {
        java.sql.Timestamp utcTimestamp = java.sql.Timestamp.valueOf(
                java.time.LocalDateTime.ofInstant(scheduledAt, java.time.ZoneOffset.UTC));
        jdbcTemplate.update("""
                INSERT INTO standups (batch_id, scheduled_at, late_cutoff_minutes, status)
                VALUES (?, ?, ?, 'SCHEDULED')
                """, batchId, utcTimestamp, lateCutoffMinutes);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM standups WHERE batch_id = ? ORDER BY id DESC LIMIT 1", Long.class, batchId);
    }

    private String uuidOf(String accessToken) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
            .when()
                .get("/api/v1/users/me")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.uuid");
    }

    private long currentUserId(String accessToken) {
        String uuid = uuidOf(accessToken);
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

    private String registerVerifyGrantPmRoleAndLogin(String fullName) {
        String email = uniqueEmail(fullName);
        given()
                .contentType("application/json")
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery"))
            .when()
                .post("/api/v1/auth/register")
            .then()
                .statusCode(201);
        verifyEmailDirectly(email);
        grantTrainerPmRole(email);
        return login(email);
    }

    private void grantTrainerPmRole(String email) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = 'TRAINER_PM'", Long.class);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    /** {@code POST /auth/register} always grants {@code STUDENT} (feature 03's {@code
     * AuthService.register} — every self-registered account is a STUDENT by default; confirmed
     * the hard way when {@code list_asClient_returns403} first tried a plain grant-on-top-of-
     * register helper and got 200, not 403, because the caller's token carried STUDENT *and*
     * CLIENT — and {@code StandupController.list}'s own {@code @PreAuthorize} already allows
     * STUDENT). To build a caller that is genuinely outside {TRAINER_PM, ADMIN, STUDENT}, the
     * default STUDENT row has to be removed, not just added to — done directly via JDBC (the
     * real admin-driven path, {@code AdminUserController.updateRoles}, is the same "replace, not
     * append" operation but needs an ADMIN caller and its own mandatory-2FA setup, more machinery
     * than this fixture needs). Roles are baked into the JWT at login time (not re-read from the
     * DB per request — {@code tier} is the one entitlement that works that way, roles don't), so
     * the login has to happen *after* the role swap for the issued token to reflect it. */
    private String registerAndLoginWithOnlyRole(String fullName, String roleCode) {
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
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return login(email);
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

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}
