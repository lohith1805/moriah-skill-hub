package com.moriah.skillhub;

import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import io.restassured.RestAssured;
import io.restassured.response.Response;
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

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * build-plan.md feature 11 verify line, exercised end to end over real HTTP: "Illegal transition
 * returns 409. Velocity is accurate. Assignment windows can be created and listed per batch."
 * Real HTTP + JWT auth throughout (same reasoning as {@code BatchFlowIT}: this needs the real
 * {@code @PreAuthorize}/ownership/state-machine stack, not mocks) — can't use {@code
 * @Transactional} rollback for the same reason, so every test tracks and cleans up its own rows.
 */
class SprintTaskFlowIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> insertedBatchIds = new ArrayList<>();

    // Sprint / batch / assignment-window dates are relative to "now" so @FutureOrPresent on
    // CreateSprintRequest / CreateBatchRequest.startDate never trips as the calendar advances
    // past a hardcoded literal (this suite failed suite-wide on 2026-09-02 when a formerly-future
    // W1_START became yesterday). W1 and W2 are consecutive 7-day weeks; W1_OVERLAP straddles
    // W1's second half.
    private static final String W1_START = java.time.LocalDate.now().plusDays(1).toString();
    private static final String W1_END = java.time.LocalDate.now().plusDays(7).toString();
    private static final String W2_START = java.time.LocalDate.now().plusDays(8).toString();
    private static final String W2_END = java.time.LocalDate.now().plusDays(14).toString();
    private static final String W1_OVERLAP_START = java.time.LocalDate.now().plusDays(5).toString();
    private static final String W1_OVERLAP_END = java.time.LocalDate.now().plusDays(11).toString();

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @AfterEach
    void cleanUp() {
        if (!insertedBatchIds.isEmpty()) {
            String placeholders = placeholders(insertedBatchIds.size());
            jdbcTemplate.update("""
                    DELETE aw FROM assignment_windows aw WHERE aw.batch_id IN (%s)
                    """.formatted(placeholders), insertedBatchIds.toArray());
            jdbcTemplate.update("""
                    DELETE t FROM tasks t JOIN sprints s ON s.id = t.sprint_id WHERE s.batch_id IN (%s)
                    """.formatted(placeholders), insertedBatchIds.toArray());
            jdbcTemplate.update("DELETE FROM sprints WHERE batch_id IN (%s)".formatted(placeholders),
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
    void createSprint_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Sprint Bystander");
        long batchId = 1L; // never reached — the role check runs before any lookup

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(sprintBody(batchId, 1, W1_START, W1_END, 20))
        .when()
                .post("/api/v1/sprints")
        .then()
                .statusCode(403);
    }

    @Test
    void pullTask_asTrainerPm_returns403() {
        String pmToken = registerVerifyGrantRoleAndLogin("Pull PM", "TRAINER_PM");
        long batchId = createBatch(pmToken, "PULL_ROLE_TRACK", 10);
        long sprintId = createSprint(pmToken, batchId, 1, W1_START, W1_END, 20)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        long taskId = createTask(pmToken, sprintId, "Role-gated task").then().statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .header("Authorization", "Bearer " + pmToken)
        .when()
                .post("/api/v1/tasks/" + taskId + "/pull")
        .then()
                .statusCode(403);
    }

    // ---------------------------------------------------------------------------------------
    // Sprint state machine
    // ---------------------------------------------------------------------------------------

    @Test
    void createSprint_overlappingDates_returns409() {
        String pmToken = registerVerifyGrantRoleAndLogin("Overlap PM", "TRAINER_PM");
        long batchId = createBatch(pmToken, "OVERLAP_TRACK", 10);
        createSprint(pmToken, batchId, 1, W1_START, W1_END, 20).then().statusCode(201);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(sprintBody(batchId, 2, W1_OVERLAP_START, W1_OVERLAP_END, 20))
        .when()
                .post("/api/v1/sprints")
        .then()
                .statusCode(409)
                .body("error.code", equalTo("SPRINT_DATE_OVERLAP"));
    }

    @Test
    void activateSprint_secondSprintBeforeFirstCompleted_returns409() {
        String pmToken = registerVerifyGrantRoleAndLogin("Sequence PM", "TRAINER_PM");
        long batchId = createBatch(pmToken, "SEQUENCE_TRACK", 10);
        long sprint1 = createSprint(pmToken, batchId, 1, W1_START, W1_END, 20)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        long sprint2 = createSprint(pmToken, batchId, 2, W2_START, W2_END, 20)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");

        activateSprint(pmToken, sprint1).then().statusCode(200).body("data.status", equalTo("ACTIVE"));

        // sprint 1 is ACTIVE, not COMPLETED — activating sprint 2 is an illegal transition.
        activateSprint(pmToken, sprint2).then()
                .statusCode(409)
                .body("error.code", equalTo("SPRINT_PREVIOUS_NOT_COMPLETED"));

        // a second ACTIVE sprint in the same batch is independently illegal even for sprint 1
        // itself — re-activating an already-ACTIVE sprint.
        activateSprint(pmToken, sprint1).then()
                .statusCode(409)
                .body("error.code", equalTo("SPRINT_INVALID_TRANSITION"));
    }

    // ---------------------------------------------------------------------------------------
    // Task state machine
    // ---------------------------------------------------------------------------------------

    @Test
    void pullTask_studentNotActiveBatchMember_returns403() {
        String pmToken = registerVerifyGrantRoleAndLogin("Membership PM", "TRAINER_PM");
        String outsiderToken = registerVerifyAndLogin("Outsider Student");
        long batchId = createBatch(pmToken, "MEMBERSHIP_TRACK", 10);
        long sprintId = createSprint(pmToken, batchId, 1, W1_START, W1_END, 20)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        long taskId = createTask(pmToken, sprintId, "Members only").then().statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .header("Authorization", "Bearer " + outsiderToken)
        .when()
                .post("/api/v1/tasks/" + taskId + "/pull")
        .then()
                .statusCode(403)
                .body("error.code", equalTo("NOT_BATCH_MEMBER"));
    }

    @Test
    void pullTask_alreadyAssigned_returns409IllegalTransition() {
        String pmToken = registerVerifyGrantRoleAndLogin("Double Pull PM", "TRAINER_PM");
        String studentAToken = registerVerifyAndLogin("Puller A");
        String studentBToken = registerVerifyAndLogin("Puller B");
        long batchId = createBatch(pmToken, "DOUBLE_PULL_TRACK", 10);
        addStudent(pmToken, batchId, uuidFromToken(studentAToken));
        addStudent(pmToken, batchId, uuidFromToken(studentBToken));
        long sprintId = createSprint(pmToken, batchId, 1, W1_START, W1_END, 20)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        long taskId = createTask(pmToken, sprintId, "One puller wins").then().statusCode(201)
                .extract().jsonPath().getLong("data.id");

        pullTask(studentAToken, taskId).then().statusCode(200).body("data.status", equalTo("ASSIGNED"));

        // build-plan.md feature 11 verify line: "Illegal transition returns 409" — a second pull
        // (by anyone) on a task that's no longer BACKLOG.
        pullTask(studentBToken, taskId).then()
                .statusCode(409)
                .body("error.code", equalTo("TASK_INVALID_TRANSITION"));
        pullTask(studentAToken, taskId).then()
                .statusCode(409)
                .body("error.code", equalTo("TASK_INVALID_TRANSITION"));
    }

    @Test
    void assignTask_studentNotActiveBatchMember_returns403() {
        String pmToken = registerVerifyGrantRoleAndLogin("Assign PM", "TRAINER_PM");
        String outsiderUuid = registerAndGetUuid("Assign Outsider");
        long batchId = createBatch(pmToken, "ASSIGN_MEMBERSHIP_TRACK", 10);
        long sprintId = createSprint(pmToken, batchId, 1, W1_START, W1_END, 20)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        long taskId = createTask(pmToken, sprintId, "Assign target").then().statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("userUuid", outsiderUuid))
        .when()
                .post("/api/v1/tasks/" + taskId + "/assign")
        .then()
                .statusCode(403)
                .body("error.code", equalTo("NOT_BATCH_MEMBER"));
    }

    @Test
    void updateTask_skipsInReview_returns409IllegalTransition() {
        String pmToken = registerVerifyGrantRoleAndLogin("Skip PM", "TRAINER_PM");
        String studentToken = registerVerifyAndLogin("Skip Student");
        long batchId = createBatch(pmToken, "SKIP_TRACK", 10);
        addStudent(pmToken, batchId, uuidFromToken(studentToken));
        long sprintId = createSprint(pmToken, batchId, 1, W1_START, W1_END, 20)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        long taskId = createTask(pmToken, sprintId, "No skipping").then().statusCode(201)
                .extract().jsonPath().getLong("data.id");

        pullTask(studentToken, taskId).then().statusCode(200);
        updateTaskStatus(pmToken, taskId, "IN_PROGRESS", 5).then().statusCode(200);

        // IN_PROGRESS -> COMPLETED skips IN_REVIEW — illegal per the state machine diagram.
        updateTaskStatus(pmToken, taskId, "COMPLETED", 5).then()
                .statusCode(409)
                .body("error.code", equalTo("TASK_INVALID_TRANSITION"));
    }

    @Test
    void updateTask_backlogToAssignedViaPut_returns409() {
        String pmToken = registerVerifyGrantRoleAndLogin("Bypass PM", "TRAINER_PM");
        long batchId = createBatch(pmToken, "BYPASS_TRACK", 10);
        long sprintId = createSprint(pmToken, batchId, 1, W1_START, W1_END, 20)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        long taskId = createTask(pmToken, sprintId, "No PUT bypass").then().statusCode(201)
                .extract().jsonPath().getLong("data.id");

        updateTaskStatus(pmToken, taskId, "ASSIGNED", 5).then()
                .statusCode(409)
                .body("error.code", equalTo("TASK_INVALID_TRANSITION"));
    }

    // ---------------------------------------------------------------------------------------
    // Full flow: task completion rolls up onto sprint.completed_points; velocity is accurate
    // ---------------------------------------------------------------------------------------

    @Test
    void fullFlow_taskCompletion_rollsUpOntoSprintAndVelocityIsAccurate() {
        String pmToken = registerVerifyGrantRoleAndLogin("Velocity PM", "TRAINER_PM");
        String studentToken = registerVerifyAndLogin("Velocity Student");
        long batchId = createBatch(pmToken, "VELOCITY_TRACK", 10);
        addStudent(pmToken, batchId, uuidFromToken(studentToken));

        long sprint1 = createSprint(pmToken, batchId, 1, W1_START, W1_END, 20)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        long sprint2 = createSprint(pmToken, batchId, 2, W2_START, W2_END, 20)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");

        long taskId = createTask(pmToken, sprint1, "Ship the feature").then().statusCode(201)
                .extract().jsonPath().getLong("data.id");

        activateSprint(pmToken, sprint1).then().statusCode(200);

        pullTask(studentToken, taskId).then().statusCode(200).body("data.status", equalTo("ASSIGNED"));
        updateTaskStatus(pmToken, taskId, "IN_PROGRESS", 8).then().statusCode(200);
        updateTaskStatus(pmToken, taskId, "IN_REVIEW", 8).then().statusCode(200);
        updateTaskStatus(pmToken, taskId, "COMPLETED", 8).then()
                .statusCode(200)
                .body("data.status", equalTo("COMPLETED"))
                .body("data.completedAt", org.hamcrest.Matchers.notNullValue());

        // completed_points rolled up onto the sprint immediately (build-plan.md feature 11:
        // "completed_points recalculated on completion")
        listSprints(pmToken, batchId).then()
                .statusCode(200)
                .body("data.content.find { it.id == " + sprint1 + " }.completedPoints", equalTo(8));

        // no dedicated "complete a sprint" endpoint — PUT drives it, same invariants as /activate
        updateSprintStatus(pmToken, sprint1, "COMPLETED").then().statusCode(200)
                .body("data.status", equalTo("COMPLETED"));

        // now legal — the previous sprint is COMPLETED
        activateSprint(pmToken, sprint2).then().statusCode(200).body("data.status", equalTo("ACTIVE"));

        // build-plan.md feature 11 verify line: "Velocity is accurate" — code-standards.md's own
        // formula, proven directly against the database (no dedicated endpoint exists for this).
        Long velocity = jdbcTemplate.queryForObject("""
                SELECT SUM(completed_points) FROM sprints WHERE batch_id = ? AND status = 'COMPLETED'
                """, Long.class, batchId);
        assertThat(velocity).isEqualTo(8L);
    }

    // ---------------------------------------------------------------------------------------
    // Assignment windows
    // ---------------------------------------------------------------------------------------

    @Test
    void assignmentWindows_createAndList_perBatch() {
        String pmToken = registerVerifyGrantRoleAndLogin("Window PM", "TRAINER_PM");
        long batchId = createBatch(pmToken, "WINDOW_TRACK", 10);

        createAssignmentWindow(pmToken, batchId, W1_START, W1_END, "2026-09-07T18:00:00Z", null)
                .then().statusCode(201)
                .body("data.batchId", equalTo((int) batchId))
                .body("data.taskId", org.hamcrest.Matchers.nullValue());

        createAssignmentWindow(pmToken, batchId, W2_START, W2_END, "2026-09-14T18:00:00Z", null)
                .then().statusCode(201);

        // build-plan.md feature 11 verify line: "Assignment windows can be created and listed
        // per batch" — no dedicated GET endpoint is named in the endpoint list; this proves the
        // one this feature adds to satisfy the verify line.
        given()
                .header("Authorization", "Bearer " + pmToken)
                .queryParam("batchId", batchId)
        .when()
                .get("/api/v1/assignment-windows")
        .then()
                .statusCode(200)
                .body("data.content", hasSize(2))
                .body("data.content[0].weekStart", equalTo(W1_START))
                .body("data.content[1].weekStart", equalTo(W2_START));

        // duplicate (batch, week_start) — the unique constraint's own business-rule mirror
        createAssignmentWindow(pmToken, batchId, W1_START, W1_END, "2026-09-07T18:00:00Z", null)
                .then().statusCode(409).body("error.code", equalTo("ASSIGNMENT_WINDOW_WEEK_TAKEN"));
    }

    @Test
    void assignmentWindows_linkedTaskFromAnotherBatch_returns409() {
        String pmToken = registerVerifyGrantRoleAndLogin("Cross Batch PM", "TRAINER_PM");
        long batchId = createBatch(pmToken, "WINDOW_HOME_TRACK", 10);
        long otherBatchId = createBatch(pmToken, "WINDOW_OTHER_TRACK", 10);
        long otherSprintId = createSprint(pmToken, otherBatchId, 1, W1_START, W1_END, 20)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        long foreignTaskId = createTask(pmToken, otherSprintId, "Belongs elsewhere").then().statusCode(201)
                .extract().jsonPath().getLong("data.id");

        createAssignmentWindow(pmToken, batchId, W1_START, W1_END, "2026-09-07T18:00:00Z", foreignTaskId)
                .then().statusCode(409).body("error.code", equalTo("ASSIGNMENT_WINDOW_TASK_WRONG_BATCH"));
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private Map<String, Object> sprintBody(long batchId, int sprintNumber, String startDate, String endDate, int plannedPoints) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("batchId", batchId);
        body.put("sprintNumber", sprintNumber);
        body.put("goal", "Sprint " + sprintNumber + " goal");
        body.put("startDate", startDate);
        body.put("endDate", endDate);
        body.put("plannedPoints", plannedPoints);
        return body;
    }

    private Response createSprint(String pmToken, long batchId, int sprintNumber, String startDate, String endDate, int plannedPoints) {
        return given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(sprintBody(batchId, sprintNumber, startDate, endDate, plannedPoints))
            .when()
                .post("/api/v1/sprints");
    }

    private Response activateSprint(String pmToken, long sprintId) {
        return given()
                .header("Authorization", "Bearer " + pmToken)
            .when()
                .post("/api/v1/sprints/" + sprintId + "/activate");
    }

    private Response updateSprintStatus(String pmToken, long sprintId, String status) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("goal", "Updated goal");
        body.put("startDate", W1_START);
        body.put("endDate", W1_END);
        body.put("plannedPoints", 20);
        body.put("status", status);
        return given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(body)
            .when()
                .put("/api/v1/sprints/" + sprintId);
    }

    private Response listSprints(String pmToken, long batchId) {
        return given()
                .header("Authorization", "Bearer " + pmToken)
                .queryParam("batchId", batchId)
            .when()
                .get("/api/v1/sprints");
    }

    private Response createTask(String pmToken, long sprintId, String title) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("sprintId", sprintId);
        body.put("title", title);
        body.put("description", "Task description");
        body.put("taskType", "STORY");
        body.put("storyPoints", 5);
        return given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(body)
            .when()
                .post("/api/v1/tasks");
    }

    private Response updateTaskStatus(String pmToken, long taskId, String status, int storyPoints) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", "Task title");
        body.put("description", "Task description");
        body.put("taskType", "STORY");
        body.put("storyPoints", storyPoints);
        body.put("status", status);
        return given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(body)
            .when()
                .put("/api/v1/tasks/" + taskId);
    }

    private Response pullTask(String studentToken, long taskId) {
        return given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/tasks/" + taskId + "/pull");
    }

    private Response createAssignmentWindow(String pmToken, long batchId, String weekStart, String weekEnd,
            String dueAt, Long taskId) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("batchId", batchId);
        body.put("weekStart", weekStart);
        body.put("weekEnd", weekEnd);
        body.put("dueAt", dueAt);
        if (taskId != null) {
            body.put("taskId", taskId);
        }
        return given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(body)
            .when()
                .post("/api/v1/assignment-windows");
    }

    private long createBatch(String pmToken, String trackCode, int capacity) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", "Batch " + UUID.randomUUID());
        body.put("trackCode", trackCode);
        body.put("startDate", java.time.LocalDate.now().toString());
        body.put("endDate", "2026-12-01");
        body.put("capacity", capacity);
        long batchId = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(body)
            .when()
                .post("/api/v1/batches")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");
        insertedBatchIds.add(batchId);
        return batchId;
    }

    private void addStudent(String pmToken, long batchId, String studentUuid) {
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("userUuid", studentUuid))
            .when()
                .post("/api/v1/batches/" + batchId + "/students")
            .then()
                .statusCode(200);
    }

    private String uuidFromToken(String accessToken) {
        return given()
                .header("Authorization", "Bearer " + accessToken)
            .when()
                .get("/api/v1/users/me")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.uuid");
    }

    private String registerAndGetUuid(String fullName) {
        String email = uniqueEmail(fullName);
        Response response = given()
                .contentType("application/json")
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery", "agreedToTerms", true))
            .when()
                .post("/api/v1/auth/register")
            .then()
                .statusCode(201)
                .extract().response();

        verifyEmailDirectly(email);
        return response.jsonPath().getString("data.uuid");
    }

    private String registerVerifyAndLogin(String fullName) {
        String email = uniqueEmail(fullName);
        given()
                .contentType("application/json")
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery", "agreedToTerms", true))
            .when()
                .post("/api/v1/auth/register")
            .then()
                .statusCode(201);
        verifyEmailDirectly(email);
        return login(email);
    }

    /** Only {@code TRAINER_PM} is granted here — neither carries mandatory 2FA
     * (build-plan.md feature 05), unlike {@code ADMIN}/{@code HR_MANAGER}, so no challenge-token
     * dance is needed (contrast {@code BatchFlowIT.registerVerifyGrantRoleAndLogin}). */
    private String registerVerifyGrantRoleAndLogin(String fullName, String roleCode) {
        String email = uniqueEmail(fullName);
        given()
                .contentType("application/json")
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery", "agreedToTerms", true))
            .when()
                .post("/api/v1/auth/register")
            .then()
                .statusCode(201);
        verifyEmailDirectly(email);
        grantRole(email, roleCode);
        return login(email);
    }

    private void grantRole(String email, String roleCode) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
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
                .body("data.tokens.accessToken", org.hamcrest.Matchers.notNullValue())
                .extract().jsonPath().getString("data.tokens.accessToken");
    }

    private String uniqueEmail(String fullName) {
        return fullName.toLowerCase().replace(" ", ".") + "-" + UUID.randomUUID() + "@example.com";
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}
