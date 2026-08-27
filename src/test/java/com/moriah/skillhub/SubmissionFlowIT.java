package com.moriah.skillhub;

import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import com.moriah.skillhub.submission.gateway.GithubPrFetcher;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * build-plan.md feature 12 verify line: "Submitting another user's PR returns 403. Malformed URL
 * returns 400. Double-POST creates one row. A weekly UNSATISFACTORY is queryable per student per
 * week." {@code GithubPrFetcher} is replaced with a Mockito mock — the lowest-level HTTP-calling
 * bean, not a whole-service mock — since GitHub is an external, non-Testcontainers-able
 * dependency and no real PAT exists in CI (`/architect feature 12` decision, same "mocked at the
 * SDK/REST boundary" reasoning Razorpay/Stripe already use, just applied via Spring's
 * bean-replacement mechanism since this feature's own HTTP-level behavior can't be proven without
 * going through the real controller -> service path around it).
 */
class SubmissionFlowIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private GithubPrFetcher githubPrFetcher;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void create_malformedUrl_returns400() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("PM Malformed");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("FULL_STACK", pmUserId);
        long sprintId = insertSprint(batchId, 1);
        String studentToken = registerVerifyAndLogin("Student Malformed");
        long studentUserId = currentUserId(studentToken);
        setGithubUsername(studentUserId, "student-malformed");
        long taskId = insertTask(sprintId, studentUserId);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("taskId", taskId, "prUrl", "https://gitlab.com/owner/repo/merge_requests/1"))
            .when()
                .post("/api/v1/submissions")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("INVALID_PR_URL"));
    }

    @Test
    void create_anotherUsersPr_returns403() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("PM Mismatch");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("FULL_STACK", pmUserId);
        long sprintId = insertSprint(batchId, 1);
        String studentToken = registerVerifyAndLogin("Student Mismatch");
        long studentUserId = currentUserId(studentToken);
        setGithubUsername(studentUserId, "real-student");
        long taskId = insertTask(sprintId, studentUserId);

        when(githubPrFetcher.fetch("someone", "repo", 7)).thenReturn(
                new GithubPrFetcher.PullRequestDto(
                        new GithubPrFetcher.PullRequestDto.GithubUser("someone-else"),
                        "open", false, 2, new GithubPrFetcher.PullRequestDto.Head("sha1")));

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("taskId", taskId, "prUrl", "https://github.com/someone/repo/pull/7"))
            .when()
                .post("/api/v1/submissions")
            .then()
                .statusCode(403)
                .body("error.code", equalTo("PR_AUTHOR_MISMATCH"));
    }

    @Test
    void create_verifiedSuccessfully_movesTaskToInReviewAndAppearsInReviewQueue() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("PM Success");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("FULL_STACK", pmUserId);
        long sprintId = insertSprint(batchId, 1);
        String studentToken = registerVerifyAndLogin("Student Success");
        long studentUserId = currentUserId(studentToken);
        setGithubUsername(studentUserId, "octocat");
        long taskId = insertTask(sprintId, studentUserId);

        when(githubPrFetcher.fetch("octocat", "hello-world", 1)).thenReturn(
                new GithubPrFetcher.PullRequestDto(
                        new GithubPrFetcher.PullRequestDto.GithubUser("octocat"),
                        "open", false, 4, new GithubPrFetcher.PullRequestDto.Head("commitsha1")));

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("taskId", taskId, "prUrl", "https://github.com/octocat/hello-world/pull/1"))
            .when()
                .post("/api/v1/submissions")
            .then()
                .statusCode(201)
                .body("data.attemptNumber", equalTo(1))
                .body("data.prState", equalTo("OPEN"))
                .body("data.commitCount", equalTo(4));

        String taskStatus = jdbcTemplate.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, taskId);
        assertThat(taskStatus).isEqualTo("IN_REVIEW");

        given()
                .header("Authorization", "Bearer " + pmToken)
            .when()
                .get("/api/v1/reviews/queue")
            .then()
                .statusCode(200)
                .body("data.content.find { it.id == " + taskId + " }.status", equalTo("IN_REVIEW"));
    }

    @Test
    void review_approved_completesTheTask() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("PM Approve");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("FULL_STACK", pmUserId);
        long sprintId = insertSprint(batchId, 1);
        String studentToken = registerVerifyAndLogin("Student Approve");
        long studentUserId = currentUserId(studentToken);
        setGithubUsername(studentUserId, "approve-student");
        long taskId = insertTask(sprintId, studentUserId);

        when(githubPrFetcher.fetch("approve-student", "repo", 1)).thenReturn(
                new GithubPrFetcher.PullRequestDto(
                        new GithubPrFetcher.PullRequestDto.GithubUser("approve-student"),
                        "open", false, 1, new GithubPrFetcher.PullRequestDto.Head("sha1")));

        long submissionId = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("taskId", taskId, "prUrl", "https://github.com/approve-student/repo/pull/1"))
            .when()
                .post("/api/v1/submissions")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("submissionId", submissionId, "score", 9, "verdict", "APPROVED", "comments", "Nice work"))
            .when()
                .post("/api/v1/reviews")
            .then()
                .statusCode(201)
                .body("data.verdict", equalTo("APPROVED"));

        String taskStatus = jdbcTemplate.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, taskId);
        assertThat(taskStatus).isEqualTo("COMPLETED");
    }

    @Test
    void review_changesRequested_sendsTaskBackToInProgressAndAllowsResubmission() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("PM ChangesRequested");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("FULL_STACK", pmUserId);
        long sprintId = insertSprint(batchId, 1);
        String studentToken = registerVerifyAndLogin("Student ChangesRequested");
        long studentUserId = currentUserId(studentToken);
        setGithubUsername(studentUserId, "cr-student");
        long taskId = insertTask(sprintId, studentUserId);

        when(githubPrFetcher.fetch("cr-student", "repo", 1)).thenReturn(
                new GithubPrFetcher.PullRequestDto(
                        new GithubPrFetcher.PullRequestDto.GithubUser("cr-student"),
                        "open", false, 1, new GithubPrFetcher.PullRequestDto.Head("sha1")));
        long submissionId = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("taskId", taskId, "prUrl", "https://github.com/cr-student/repo/pull/1"))
            .when()
                .post("/api/v1/submissions")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("submissionId", submissionId, "score", 3, "verdict", "CHANGES_REQUESTED", "comments", "Fix tests"))
            .when()
                .post("/api/v1/reviews")
            .then()
                .statusCode(201);

        String taskStatus = jdbcTemplate.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, taskId);
        assertThat(taskStatus).isEqualTo("IN_PROGRESS");

        // resubmission — a second attempt, not a duplicate of the first
        when(githubPrFetcher.fetch("cr-student", "repo", 2)).thenReturn(
                new GithubPrFetcher.PullRequestDto(
                        new GithubPrFetcher.PullRequestDto.GithubUser("cr-student"),
                        "open", false, 2, new GithubPrFetcher.PullRequestDto.Head("sha2")));
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("taskId", taskId, "prUrl", "https://github.com/cr-student/repo/pull/2"))
            .when()
                .post("/api/v1/submissions")
            .then()
                .statusCode(201)
                .body("data.attemptNumber", equalTo(2));
    }

    @Test
    void create_concurrentDoublePost_createsExactlyOneRowForThatAttempt() throws Exception {
        String pmToken = registerVerifyGrantPmRoleAndLogin("PM Concurrent");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("FULL_STACK", pmUserId);
        long sprintId = insertSprint(batchId, 1);
        String studentToken = registerVerifyAndLogin("Student Concurrent");
        long studentUserId = currentUserId(studentToken);
        setGithubUsername(studentUserId, "concurrent-student");
        long taskId = insertTask(sprintId, studentUserId);

        when(githubPrFetcher.fetch(anyString(), anyString(), anyInt())).thenReturn(
                new GithubPrFetcher.PullRequestDto(
                        new GithubPrFetcher.PullRequestDto.GithubUser("concurrent-student"),
                        "open", false, 1, new GithubPrFetcher.PullRequestDto.Head("sha1")));

        Map<String, Object> body = Map.of("taskId", taskId, "prUrl", "https://github.com/concurrent-student/repo/pull/1");
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Callable<Integer> submit = () -> given()
                    .contentType("application/json")
                    .header("Authorization", "Bearer " + studentToken)
                    .body(body)
                .when()
                    .post("/api/v1/submissions")
                .then()
                    .extract().statusCode();

            List<Future<Integer>> futures = executor.invokeAll(List.of(submit, submit));
            for (Future<Integer> future : futures) {
                assertThat(future.get()).isEqualTo(201);
            }
        } finally {
            executor.shutdown();
        }

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_submissions WHERE task_id = ? AND user_id = ? AND attempt_number = 1",
                Long.class, taskId, studentUserId);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void weeklyReview_unsatisfactory_isQueryablePerStudentPerWeek() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("PM Weekly");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("FULL_STACK", pmUserId);
        long sprintId = insertSprint(batchId, 1);
        String studentUuid = registerAndGetUuid("Student Weekly");
        LocalDate weekStart = LocalDate.of(2026, 9, 7);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of(
                        "userUuid", studentUuid,
                        "batchId", batchId,
                        "sprintId", sprintId,
                        "weekStart", weekStart.toString(),
                        "rating", "UNSATISFACTORY",
                        "notes", "Missed three standups, two overdue tasks"))
            .when()
                .post("/api/v1/reviews/weekly")
            .then()
                .statusCode(201)
                .body("data.rating", equalTo("UNSATISFACTORY"));

        String rating = jdbcTemplate.queryForObject("""
                SELECT wr.rating FROM weekly_reviews wr
                JOIN users u ON u.id = wr.user_id
                WHERE u.uuid = ? AND wr.week_start = ?
                """, String.class, studentUuid, java.sql.Date.valueOf(weekStart));
        assertThat(rating).isEqualTo("UNSATISFACTORY");
    }

    // ---------------------------------------------------------------------------------------
    // Role gates
    // ---------------------------------------------------------------------------------------
    // `/review` finding: every one of this feature's four role-restricted endpoints lacked a
    // wrong-role 403 test, unlike every other feature's established pattern (e.g.
    // SprintTaskFlowIT.createSprint_asStudent_returns403). `@PreAuthorize` runs before any
    // lookup, so a bogus/never-reached id is deliberate, same as that precedent.

    @Test
    void create_asTrainerPm_returns403() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("PM Submitting");
        long taskId = 1L; // never reached — the role check runs before any lookup

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("taskId", taskId, "prUrl", "https://github.com/owner/repo/pull/1"))
            .when()
                .post("/api/v1/submissions")
            .then()
                .statusCode(403);
    }

    @Test
    void reviewQueue_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Student Queue Bystander");

        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .get("/api/v1/reviews/queue")
            .then()
                .statusCode(403);
    }

    @Test
    void review_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Student Reviewing");
        long submissionId = 1L; // never reached — the role check runs before any lookup

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("submissionId", submissionId, "score", 8, "verdict", "APPROVED"))
            .when()
                .post("/api/v1/reviews")
            .then()
                .statusCode(403);
    }

    @Test
    void weeklyReview_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Student Weekly Reviewing");
        long batchId = 1L; // never reached — the role check runs before any lookup

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of(
                        "userUuid", "not-a-real-uuid",
                        "batchId", batchId,
                        "sprintId", 1L,
                        "weekStart", "2026-09-07",
                        "rating", "SATISFACTORY"))
            .when()
                .post("/api/v1/reviews/weekly")
            .then()
                .statusCode(403);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private long insertBatch(String trackCode, long pmUserId) {
        jdbcTemplate.update("""
                INSERT INTO batches (name, track_code, pm_id, start_date, end_date, capacity, status)
                VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 90 DAY, 20, 'ACTIVE')
                """, "Batch-" + UUID.randomUUID(), trackCode, pmUserId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM batches WHERE track_code = ? AND pm_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, trackCode, pmUserId);
    }

    private long insertSprint(long batchId, int sprintNumber) {
        jdbcTemplate.update("""
                INSERT INTO sprints (batch_id, sprint_number, start_date, end_date, status)
                VALUES (?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 7 DAY, 'ACTIVE')
                """, batchId, sprintNumber);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sprints WHERE batch_id = ? AND sprint_number = ?", Long.class, batchId, sprintNumber);
    }

    /** Bypasses the assign/pull dance — status goes straight to {@code IN_PROGRESS} with {@code
     * assignedTo} already set, the state {@code SubmissionService.create} actually needs (test
     * setup convenience, same reasoning other ITs bypass unrelated flows). */
    private long insertTask(long sprintId, long assignedToUserId) {
        jdbcTemplate.update("""
                INSERT INTO tasks (sprint_id, title, task_type, assigned_to, status)
                VALUES (?, ?, 'ASSIGNMENT', ?, 'IN_PROGRESS')
                """, sprintId, "Task-" + UUID.randomUUID(), assignedToUserId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tasks WHERE sprint_id = ? AND assigned_to = ? ORDER BY id DESC LIMIT 1",
                Long.class, sprintId, assignedToUserId);
    }

    private void setGithubUsername(long userId, String githubUsername) {
        jdbcTemplate.update("UPDATE users SET github_username = ? WHERE id = ?", githubUsername, userId);
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

    private String registerAndGetUuid(String fullName) {
        String email = uniqueEmail(fullName);
        Response response = given()
                .contentType("application/json")
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery"))
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
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery"))
            .when()
                .post("/api/v1/auth/register")
            .then()
                .statusCode(201);
        verifyEmailDirectly(email);
        return login(email);
    }

    /** Every "PM ..." fixture in this file needs the real {@code TRAINER_PM} role granted —
     * {@code POST /reviews}/{@code GET /reviews/queue}/{@code POST /reviews/weekly} are real,
     * role-gated controller calls (unlike the batch/sprint/task setup, which bypasses the
     * controller layer entirely via direct JDBC inserts). */
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
