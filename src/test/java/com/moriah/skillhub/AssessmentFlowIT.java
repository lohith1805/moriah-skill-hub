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
 * build-plan.md feature 14 verify line: "Attempt payload contains no correct answers. A quiz
 * that is half CODE questions yields a percentage based only on the auto-gradable half, and the
 * attempt is flagged for manual grading." Real HTTP + JWT auth throughout (same reasoning as
 * {@code AttendanceFlowIT}) — every test tracks and cleans up its own rows.
 */
class AssessmentFlowIT extends IntegrationTestBase {

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
                    DELETE qa FROM quiz_answers qa JOIN quiz_attempts att ON att.id = qa.attempt_id
                     JOIN quizzes q ON q.id = att.quiz_id WHERE q.batch_id IN (%s)
                    """.formatted(placeholders), insertedBatchIds.toArray());
            jdbcTemplate.update("""
                    DELETE att FROM quiz_attempts att JOIN quizzes q ON q.id = att.quiz_id
                     WHERE q.batch_id IN (%s)
                    """.formatted(placeholders), insertedBatchIds.toArray());
            jdbcTemplate.update("""
                    DELETE qq FROM quiz_questions qq JOIN quizzes q ON q.id = qq.quiz_id
                     WHERE q.batch_id IN (%s)
                    """.formatted(placeholders), insertedBatchIds.toArray());
            jdbcTemplate.update("DELETE FROM quizzes WHERE batch_id IN (%s)".formatted(placeholders),
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
    void createAssessment_asStudent_returns403() {
        // A well-formed body, not an empty one — @Valid request-body validation runs during
        // Spring MVC argument resolution, before the @PreAuthorize-guarded method invocation
        // (and its proxy) ever happens, so a body that fails validation (e.g. an empty
        // @NotEmpty questions list) surfaces as 400 regardless of role, masking the 403 this
        // test means to prove.
        String studentToken = registerVerifyAndLogin("Assessment Bystander");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("title", "x", "durationMinutes", 30, "questions", List.of(
                        Map.of("questionText", "2+2?", "questionType", "MCQ",
                                "options", List.of("3", "4"), "correctAnswerIndices", List.of(1), "marks", 10))))
            .when()
                .post("/api/v1/assessments")
            .then()
                .statusCode(403);
    }

    @Test
    void startAttempt_asTrainerPm_returns403() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Attempt Role PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("ROLE_TRACK", pmUserId);
        long quizId = createMcqQuiz(pmToken, batchId, 1, 30);

        given()
                .header("Authorization", "Bearer " + pmToken)
            .when()
                .post("/api/v1/assessments/" + quizId + "/attempts")
            .then()
                .statusCode(403);
    }

    @Test
    void submit_asTrainerPm_returns403() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Submit Role PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("SUBMIT_ROLE_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Submit Role Student");
        long studentUserId = currentUserId(studentToken);
        insertActiveMember(batchId, studentUserId);
        long quizId = createMcqQuiz(pmToken, batchId, 1, 30);

        long attemptId = given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/assessments/" + quizId + "/attempts")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("answers", List.of()))
            .when()
                .post("/api/v1/assessments/attempts/" + attemptId + "/submit")
            .then()
                .statusCode(403);
    }

    // ---------------------------------------------------------------------------------------
    // Core flow
    // ---------------------------------------------------------------------------------------

    @Test
    void create_thenList_returnsAssessmentForBatch() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("List PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("LIST_TRACK", pmUserId);
        createMcqQuiz(pmToken, batchId, 1, 30);

        given()
                .header("Authorization", "Bearer " + pmToken)
                .queryParam("batchId", batchId)
            .when()
                .get("/api/v1/assessments")
            .then()
                .statusCode(200)
                .body("data.content[0].title", equalTo("Java Basics"));
    }

    @Test
    void attemptPayload_neverContainsCorrectAnswers() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Secrecy PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("SECRECY_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Secrecy Student");
        long studentUserId = currentUserId(studentToken);
        insertActiveMember(batchId, studentUserId);
        long quizId = createMcqQuiz(pmToken, batchId, 1, 30);

        String body = given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/assessments/" + quizId + "/attempts")
            .then()
                .statusCode(201)
                .body("data.questions[0].options", org.hamcrest.Matchers.hasSize(3))
                .extract().response().asString();

        assertThat(body).doesNotContain("correctAnswer").doesNotContain("correctAnswerIndices");
    }

    @Test
    void submit_allMcqCorrect_scoresFullPercentageAndPasses() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Pass PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("PASS_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Pass Student");
        long studentUserId = currentUserId(studentToken);
        insertActiveMember(batchId, studentUserId);
        long quizId = createMcqQuiz(pmToken, batchId, 1, 30);

        long attemptId = given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/assessments/" + quizId + "/attempts")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        long questionId = jdbcTemplate.queryForObject(
                "SELECT id FROM quiz_questions WHERE quiz_id = ?", Long.class, quizId);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("answers", List.of(Map.of("questionId", questionId, "selectedOptionIndices", List.of(1)))))
            .when()
                .post("/api/v1/assessments/attempts/" + attemptId + "/submit")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("SUBMITTED"))
                .body("data.percentage", equalTo(100.0f))
                .body("data.passed", equalTo(true))
                .body("data.questions[0].isCorrect", equalTo(true));
    }

    @Test
    void submit_quizHalfCode_percentageFromAutoGradableHalfOnly_flaggedForManualGrading() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Half Code PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("HALFCODE_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Half Code Student");
        long studentUserId = currentUserId(studentToken);
        insertActiveMember(batchId, studentUserId);

        long quizId = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of(
                        "batchId", batchId, "title", "Half Code Quiz", "durationMinutes", 30,
                        "questions", List.of(
                                Map.of("questionText", "2+2?", "questionType", "MCQ",
                                        "options", List.of("3", "4"), "correctAnswerIndices", List.of(1), "marks", 10),
                                Map.of("questionText", "Write a function", "questionType", "CODE", "marks", 20))))
            .when()
                .post("/api/v1/assessments")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        long attemptId = given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/assessments/" + quizId + "/attempts")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        List<Long> questionIds = jdbcTemplate.queryForList(
                "SELECT id FROM quiz_questions WHERE quiz_id = ? ORDER BY id", Long.class, quizId);
        long mcqId = questionIds.get(0);
        long codeId = questionIds.get(1);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("answers", List.of(
                        Map.of("questionId", mcqId, "selectedOptionIndices", List.of(1)),
                        Map.of("questionId", codeId, "codeAnswer", "def solve(): pass"))))
            .when()
                .post("/api/v1/assessments/attempts/" + attemptId + "/submit")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("PENDING_MANUAL_GRADING"))
                // Only the MCQ's 10 marks count toward the denominator.
                .body("data.autoGradableMarks", equalTo(10.0f))
                .body("data.percentage", equalTo(100.0f));
    }

    @Test
    void startAttempt_maxAttemptsExceeded_returns409() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("MaxAttempts PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("MAXATTEMPTS_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("MaxAttempts Student");
        long studentUserId = currentUserId(studentToken);
        insertActiveMember(batchId, studentUserId);
        long quizId = createMcqQuiz(pmToken, batchId, 1, 30);
        long questionId = jdbcTemplate.queryForObject(
                "SELECT id FROM quiz_questions WHERE quiz_id = ?", Long.class, quizId);

        long attemptId = given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/assessments/" + quizId + "/attempts")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("answers", List.of(Map.of("questionId", questionId, "selectedOptionIndices", List.of(1)))))
            .when()
                .post("/api/v1/assessments/attempts/" + attemptId + "/submit")
            .then()
                .statusCode(200);

        // maxAttempts=1 and the only attempt is now SUBMITTED — a second start is over the limit.
        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/assessments/" + quizId + "/attempts")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("QUIZ_MAX_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void startAttempt_calledAgainWhileInProgress_resumesSameAttemptNotANewOne() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Resume PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("RESUME_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Resume Student");
        long studentUserId = currentUserId(studentToken);
        insertActiveMember(batchId, studentUserId);
        long quizId = createMcqQuiz(pmToken, batchId, 3, 30);

        long firstId = given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/assessments/" + quizId + "/attempts")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        long secondId = given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/assessments/" + quizId + "/attempts")
            .then()
                .statusCode(201)
                .body("data.attemptNumber", equalTo(1))
                .extract().jsonPath().getLong("data.id");

        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void startAttempt_concurrentDoubleStart_createsExactlyOneRowForThatAttemptNumber() throws Exception {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Concurrent PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("CONCURRENT_QUIZ_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Concurrent Quiz Student");
        long studentUserId = currentUserId(studentToken);
        insertActiveMember(batchId, studentUserId);
        long quizId = createMcqQuiz(pmToken, batchId, 5, 30);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Callable<Integer> start = () -> given()
                    .header("Authorization", "Bearer " + studentToken)
                .when()
                    .post("/api/v1/assessments/" + quizId + "/attempts")
                .then()
                    .extract().statusCode();

            List<Future<Integer>> futures = executor.invokeAll(List.of(start, start));
            for (Future<Integer> future : futures) {
                assertThat(future.get()).isEqualTo(201);
            }
        } finally {
            executor.shutdown();
        }

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quiz_attempts WHERE quiz_id = ? AND user_id = ? AND attempt_number = 1",
                Long.class, quizId, studentUserId);
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void submit_byNonOwner_returns403() {
        String pmToken = registerVerifyGrantPmRoleAndLogin("NonOwner PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("NONOWNER_TRACK", pmUserId);
        String ownerToken = registerVerifyAndLogin("Owner Student");
        long ownerUserId = currentUserId(ownerToken);
        insertActiveMember(batchId, ownerUserId);
        String intruderToken = registerVerifyAndLogin("Intruder Student");
        long intruderUserId = currentUserId(intruderToken);
        insertActiveMember(batchId, intruderUserId);
        long quizId = createMcqQuiz(pmToken, batchId, 1, 30);

        long attemptId = given()
                .header("Authorization", "Bearer " + ownerToken)
            .when()
                .post("/api/v1/assessments/" + quizId + "/attempts")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + intruderToken)
                .body(Map.of("answers", List.of()))
            .when()
                .post("/api/v1/assessments/attempts/" + attemptId + "/submit")
            .then()
                .statusCode(403);
    }

    /** Regression test for the race {@code QuizSubmissionWriter} exists to close: two concurrent
     * submits for the same attempt (double-click, or a client retry) both read {@code IN_PROGRESS}
     * before either commits. Without the {@code REQUIRES_NEW} writer, the loser's {@code
     * quiz_answers} insert violates the {@code (attempt_id, question_id)} unique constraint and
     * surfaces as a raw {@code 500} instead of being recovered as the winner's already-graded
     * result. */
    @Test
    void submit_concurrentDoubleSubmit_gradesExactlyOnce() throws Exception {
        String pmToken = registerVerifyGrantPmRoleAndLogin("Concurrent Submit PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("CONCURRENT_SUBMIT_TRACK", pmUserId);
        String studentToken = registerVerifyAndLogin("Concurrent Submit Student");
        long studentUserId = currentUserId(studentToken);
        insertActiveMember(batchId, studentUserId);
        long quizId = createMcqQuiz(pmToken, batchId, 1, 30);

        long attemptId = given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/assessments/" + quizId + "/attempts")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Callable<Integer> submit = () -> given()
                    .contentType("application/json")
                    .header("Authorization", "Bearer " + studentToken)
                    .body(Map.of("answers", List.of()))
                .when()
                    .post("/api/v1/assessments/attempts/" + attemptId + "/submit")
                .then()
                    .extract().statusCode();

            List<Future<Integer>> futures = executor.invokeAll(List.of(submit, submit));
            for (Future<Integer> future : futures) {
                assertThat(future.get()).isEqualTo(200);
            }
        } finally {
            executor.shutdown();
        }

        Long answerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quiz_answers WHERE attempt_id = ?", Long.class, attemptId);
        assertThat(answerCount).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM quiz_attempts WHERE id = ?", String.class, attemptId))
                .isEqualTo("SUBMITTED");
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private long createMcqQuiz(String pmToken, long batchId, int maxAttempts, int durationMinutes) {
        return given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of(
                        "batchId", batchId, "title", "Java Basics", "durationMinutes", durationMinutes,
                        "maxAttempts", maxAttempts,
                        "questions", List.of(Map.of(
                                "questionText", "What is 2+2?", "questionType", "MCQ",
                                "options", List.of("3", "4", "5"), "correctAnswerIndices", List.of(1), "marks", 10))))
            .when()
                .post("/api/v1/assessments")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");
    }

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

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}
