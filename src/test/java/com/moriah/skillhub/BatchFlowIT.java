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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
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
import static org.hamcrest.Matchers.notNullValue;

/**
 * build-plan.md feature 10 verify line: "Concurrent allocation to a one-seat batch yields exactly
 * one enrolment. A STARTER is never allocated. Payment -> allocation now works end to end."
 * `/architect feature 10` decisions proven here: {@code trackCode} flows checkout -> payment ->
 * webhook -> allocation; the pending queue is real rows, not just a notification; a refund
 * de-allocates. Real HTTP for the webhook and controller calls, same reasoning as {@code
 * PaymentWebhookFlowIT} — this class also can't use {@code @Transactional} rollback, so it
 * tracks and cleans up every row it commits, same pattern.
 */
class BatchFlowIT extends IntegrationTestBase {

    private static final String RAZORPAY_WEBHOOK_SECRET = "test-razorpay-webhook-secret-never-reused-anywhere-else";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> insertedPaymentIds = new ArrayList<>();
    private final List<Long> insertedPlanIds = new ArrayList<>();
    private final List<Long> insertedBatchIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @AfterEach
    void cleanUpLeakedFixtureRows() {
        waitForPendingInvoicesToSettle();
        if (!insertedPlanIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM pending_batch_allocations WHERE plan_id IN (" + placeholders(insertedPlanIds.size()) + ")",
                    insertedPlanIds.toArray());
        }
        if (!insertedBatchIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM batch_students WHERE batch_id IN (" + placeholders(insertedBatchIds.size()) + ")",
                    insertedBatchIds.toArray());
            jdbcTemplate.update("DELETE FROM batches WHERE id IN (" + placeholders(insertedBatchIds.size()) + ")",
                    insertedBatchIds.toArray());
        }
        if (!insertedPaymentIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM invoices WHERE payment_id IN (" + placeholders(insertedPaymentIds.size()) + ")",
                    insertedPaymentIds.toArray());
            jdbcTemplate.update("DELETE FROM user_subscriptions WHERE payment_id IN (" + placeholders(insertedPaymentIds.size()) + ")",
                    insertedPaymentIds.toArray());
            jdbcTemplate.update("DELETE FROM payments WHERE id IN (" + placeholders(insertedPaymentIds.size()) + ")",
                    insertedPaymentIds.toArray());
        }
        if (!insertedPlanIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM subscription_plans WHERE id IN (" + placeholders(insertedPlanIds.size()) + ")",
                    insertedPlanIds.toArray());
        }
        insertedPaymentIds.clear();
        insertedPlanIds.clear();
        insertedBatchIds.clear();
    }

    // ---------------------------------------------------------------------------------------
    // BatchController — manual CRUD and student management
    // ---------------------------------------------------------------------------------------

    @Test
    void create_asTrainerPm_createsBatchWithCallerAsPm() {
        String pmToken = registerVerifyGrantRoleAndLogin("PM One", "TRAINER_PM");

        Response response = createBatch(pmToken, "FULL_STACK", 10)
                .then()
                .statusCode(201)
                .body("data.trackCode", equalTo("FULL_STACK"))
                .body("data.capacity", equalTo(10))
                .body("data.enrolledCount", equalTo(0))
                .body("data.status", equalTo("PLANNED"))
                .extract().response();

        insertedBatchIds.add(response.jsonPath().getLong("data.id"));
    }

    @Test
    void create_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Plain Student");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of(
                        "name", "Should Fail",
                        "trackCode", "FULL_STACK",
                        "startDate", "2026-09-01",
                        "endDate", "2026-12-01",
                        "capacity", 10))
        .when()
                .post("/api/v1/batches")
        .then()
                .statusCode(403);
    }

    @Test
    void update_byNonOwningTrainerPm_returns403NotBatchOwner() {
        String ownerToken = registerVerifyGrantRoleAndLogin("PM Owner", "TRAINER_PM");
        String otherPmToken = registerVerifyGrantRoleAndLogin("PM Other", "TRAINER_PM");
        long batchId = createBatchAndTrack(ownerToken, "DATA_SCIENCE", 5);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + otherPmToken)
                .body(updateBody("Renamed", 5, "ACTIVE"))
        .when()
                .put("/api/v1/batches/" + batchId)
        .then()
                .statusCode(403)
                .body("error.code", equalTo("NOT_BATCH_OWNER"));
    }

    @Test
    void update_byAdmin_bypassesOwnership() {
        String ownerToken = registerVerifyGrantRoleAndLogin("PM Owner Two", "TRAINER_PM");
        String adminToken = registerVerifyGrantRoleAndLogin("Admin One", "ADMIN");
        long batchId = createBatchAndTrack(ownerToken, "DATA_SCIENCE", 5);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + adminToken)
                .body(updateBody("Renamed By Admin", 5, "ACTIVE"))
        .when()
                .put("/api/v1/batches/" + batchId)
        .then()
                .statusCode(200)
                .body("data.name", equalTo("Renamed By Admin"))
                .body("data.status", equalTo("ACTIVE"));
    }

    @Test
    void update_capacityBelowEnrolledCount_returns409() {
        String pmToken = registerVerifyGrantRoleAndLogin("PM Capacity", "TRAINER_PM");
        // capacity 2, both seats filled, then try to shrink to 1 — @Min(1) means capacity can
        // never legally go below enrolledCount by way of an empty/zero batch, so this needs two
        // enrolled students to actually exercise the check.
        long batchId = createBatchAndTrack(pmToken, "FULL_STACK", 2);
        addStudent(pmToken, batchId, registerAndGetUuid("Fill Seat One")).then().statusCode(200);
        addStudent(pmToken, batchId, registerAndGetUuid("Fill Seat Two")).then().statusCode(200);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(updateBody("Same Batch", 1, "PLANNED"))
        .when()
                .put("/api/v1/batches/" + batchId)
        .then()
                .statusCode(409)
                .body("error.code", equalTo("BATCH_CAPACITY_BELOW_ENROLLED"));
    }

    @Test
    void addStudent_batchAlreadyFull_returns409BatchFull() {
        String pmToken = registerVerifyGrantRoleAndLogin("PM Full", "TRAINER_PM");
        long batchId = createBatchAndTrack(pmToken, "FULL_STACK", 1);
        addStudent(pmToken, batchId, registerAndGetUuid("First Student")).then().statusCode(200);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("userUuid", registerAndGetUuid("Second Student")))
        .when()
                .post("/api/v1/batches/" + batchId + "/students")
        .then()
                .statusCode(409)
                .body("error.code", equalTo("BATCH_FULL"));
    }

    @Test
    void removeStudent_setsReassignedNotDeleted_andFreesTheSeat() {
        String pmToken = registerVerifyGrantRoleAndLogin("PM Remove", "TRAINER_PM");
        long batchId = createBatchAndTrack(pmToken, "FULL_STACK", 1);
        String studentUuid = registerAndGetUuid("Removable Student");
        addStudent(pmToken, batchId, studentUuid).then().statusCode(200);

        given()
                .header("Authorization", "Bearer " + pmToken)
        .when()
                .delete("/api/v1/batches/" + batchId + "/students/" + studentUuid)
        .then()
                .statusCode(200);

        String status = jdbcTemplate.queryForObject("""
                SELECT bs.status FROM batch_students bs
                JOIN users u ON u.id = bs.user_id
                WHERE bs.batch_id = ? AND u.uuid = ?
                """, String.class, batchId, studentUuid);
        assertThat(status).isEqualTo("REASSIGNED");

        // seat freed — a different student can now be added to the same one-capacity batch
        addStudent(pmToken, batchId, registerAndGetUuid("Replacement Student")).then().statusCode(200);
    }

    // ---------------------------------------------------------------------------------------
    // BatchAllocationService — the automatic, webhook-driven path
    // ---------------------------------------------------------------------------------------

    @Test
    void paymentCaptured_matchingBatch_allocatesTheStudentAndIncrementsEnrolledCount() {
        String pmToken = registerVerifyGrantRoleAndLogin("PM Alloc", "TRAINER_PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("FULL_STACK", null, 5, pmUserId);
        long planId = insertPlan(true, 3);
        long studentUserId = registerVerifyAndGetUserId("Alloc Student");
        String orderId = "order_" + UUID.randomUUID();
        long paymentId = insertPayment(studentUserId, planId, "FULL_STACK", orderId, "14999.00");

        captureRazorpayPayment(orderId, "pay_" + UUID.randomUUID(), 1499900);

        Long batchStudentCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM batch_students WHERE batch_id = ? AND user_id = ? AND status = 'ACTIVE'
                """, Long.class, batchId, studentUserId);
        assertThat(batchStudentCount).isEqualTo(1);

        Integer enrolledCount = jdbcTemplate.queryForObject(
                "SELECT enrolled_count FROM batches WHERE id = ?", Integer.class, batchId);
        assertThat(enrolledCount).isEqualTo(1);
    }

    @Test
    void paymentCaptured_noMatchingBatch_parksPendingAndNotifiesTrainerPms() {
        String pmToken = registerVerifyGrantRoleAndLogin("PM Pending", "TRAINER_PM");
        long pmUserId = currentUserId(pmToken);
        long planId = insertPlan(true, 3);
        long studentUserId = registerVerifyAndGetUserId("Pending Student");
        String orderId = "order_" + UUID.randomUUID();
        insertPayment(studentUserId, planId, "NO_SUCH_TRACK", orderId, "14999.00");

        captureRazorpayPayment(orderId, "pay_" + UUID.randomUUID(), 1499900);

        Long pendingCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pending_batch_allocations
                 WHERE user_id = ? AND track_code = 'NO_SUCH_TRACK' AND resolved_at IS NULL
                """, Long.class, studentUserId);
        assertThat(pendingCount).isEqualTo(1);

        waitForNotification(pmUserId, "BATCH_ALLOCATION_PENDING");
    }

    @Test
    void paymentCaptured_starterPlan_neverAllocatesAndNeverQueues() {
        long planId = insertPlan(false, 1); // STARTER-equivalent: allows_batch = FALSE
        long studentUserId = registerVerifyAndGetUserId("Starter Student");
        String orderId = "order_" + UUID.randomUUID();
        insertPayment(studentUserId, planId, "FULL_STACK", orderId, "14999.00");

        captureRazorpayPayment(orderId, "pay_" + UUID.randomUUID(), 1499900);

        Long batchStudentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM batch_students WHERE user_id = ?", Long.class, studentUserId);
        assertThat(batchStudentCount).isZero();
        Long pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pending_batch_allocations WHERE user_id = ?", Long.class, studentUserId);
        assertThat(pendingCount).isZero();
    }

    @Test
    void refund_deallocatesTheStudentAndFreesTheSeat() {
        String pmToken = registerVerifyGrantRoleAndLogin("PM Refund", "TRAINER_PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("FULL_STACK", null, 5, pmUserId);
        long planId = insertPlan(true, 3);
        long studentUserId = registerVerifyAndGetUserId("Refund Student");
        String orderId = "order_" + UUID.randomUUID();
        String gatewayPaymentId = "pay_" + UUID.randomUUID();
        insertPayment(studentUserId, planId, "FULL_STACK", orderId, "14999.00");

        captureRazorpayPayment(orderId, gatewayPaymentId, 1499900);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM batch_students WHERE batch_id = ? AND user_id = ? AND status = 'ACTIVE'",
                Long.class, batchId, studentUserId)).isEqualTo(1);

        String refundBody = """
                {"event":"refund.processed","payload":{"payment":{"entity":{"id":"%s"}},"refund":{"entity":{"id":"rfnd_%s"}}}}
                """.formatted(gatewayPaymentId, UUID.randomUUID());
        given()
                .header("X-Razorpay-Signature", razorpaySignature(refundBody))
                .contentType("application/json")
                .body(refundBody)
            .when()
                .post("/api/v1/webhooks/razorpay")
            .then()
                .statusCode(200);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM batch_students WHERE batch_id = ? AND user_id = ?",
                String.class, batchId, studentUserId);
        assertThat(status).isEqualTo("REASSIGNED");
        Integer enrolledCount = jdbcTemplate.queryForObject(
                "SELECT enrolled_count FROM batches WHERE id = ?", Integer.class, batchId);
        assertThat(enrolledCount).isZero();
    }

    /** build-plan.md feature 10 verify line, exercised for real: two payments captured
     * concurrently against a one-seat batch. The atomic {@code UPDATE ... WHERE enrolled_count <
     * capacity} (code-standards.md "Transactions") is what makes exactly one succeed — a
     * read-then-write would let both through. */
    @Test
    void concurrentAllocation_toOneSeatBatch_yieldsExactlyOneEnrolment() throws Exception {
        String pmToken = registerVerifyGrantRoleAndLogin("PM Concurrent", "TRAINER_PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("CONCURRENCY_TRACK", null, 1, pmUserId);
        long planId = insertPlan(true, 3);

        long studentA = registerVerifyAndGetUserId("Concurrent Student A");
        long studentB = registerVerifyAndGetUserId("Concurrent Student B");
        String orderA = "order_" + UUID.randomUUID();
        String orderB = "order_" + UUID.randomUUID();
        String gatewayPaymentA = "pay_" + UUID.randomUUID();
        String gatewayPaymentB = "pay_" + UUID.randomUUID();
        insertPayment(studentA, planId, "CONCURRENCY_TRACK", orderA, "14999.00");
        insertPayment(studentB, planId, "CONCURRENCY_TRACK", orderB, "14999.00");

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Callable<Integer> captureA = () -> captureRazorpayPayment(orderA, gatewayPaymentA, 1499900).statusCode();
            Callable<Integer> captureB = () -> captureRazorpayPayment(orderB, gatewayPaymentB, 1499900).statusCode();

            List<Future<Integer>> futures = executor.invokeAll(List.of(captureA, captureB));
            for (Future<Integer> future : futures) {
                assertThat(future.get()).isEqualTo(200);
            }
        } finally {
            executor.shutdown();
        }

        Long enrolledStudents = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM batch_students WHERE batch_id = ? AND status = 'ACTIVE'
                """, Long.class, batchId);
        assertThat(enrolledStudents).isEqualTo(1);

        Integer enrolledCount = jdbcTemplate.queryForObject(
                "SELECT enrolled_count FROM batches WHERE id = ?", Integer.class, batchId);
        assertThat(enrolledCount).isEqualTo(1);

        Long pendingCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pending_batch_allocations
                 WHERE user_id IN (?, ?) AND resolved_at IS NULL
                """, Long.class, studentA, studentB);
        assertThat(pendingCount).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private Response createBatch(String pmToken, String trackCode, int capacity) {
        return given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of(
                        "name", "Batch " + UUID.randomUUID(),
                        "trackCode", trackCode,
                        "startDate", "2026-09-01",
                        "endDate", "2026-12-01",
                        "capacity", capacity))
            .when()
                .post("/api/v1/batches");
    }

    private long createBatchAndTrack(String pmToken, String trackCode, int capacity) {
        long batchId = createBatch(pmToken, trackCode, capacity)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        insertedBatchIds.add(batchId);
        return batchId;
    }

    private Response addStudent(String pmToken, long batchId, String studentUuid) {
        return given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("userUuid", studentUuid))
            .when()
                .post("/api/v1/batches/" + batchId + "/students");
    }

    private Map<String, Object> updateBody(String name, int capacity, String status) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("startDate", "2026-09-01");
        body.put("endDate", "2026-12-01");
        body.put("capacity", capacity);
        body.put("status", status);
        return body;
    }

    private Response captureRazorpayPayment(String orderId, String gatewayPaymentId, long amountPaise) {
        String body = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"%s","order_id":"%s","amount":%d,"currency":"INR","status":"captured"}}}}
                """.formatted(gatewayPaymentId, orderId, amountPaise);
        return given()
                .header("X-Razorpay-Signature", razorpaySignature(body))
                .contentType("application/json")
                .body(body)
            .when()
                .post("/api/v1/webhooks/razorpay")
            .then()
                .statusCode(200)
                .extract().response();
    }

    private String razorpaySignature(String body) {
        return hmacHex(body, RAZORPAY_WEBHOOK_SECRET, "HmacSHA256");
    }

    private String hmacHex(String data, String secret, String algorithm) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Test signature computation failed", e);
        }
    }

    private long insertPlan(boolean allowsBatch, int tierRank) {
        String code = "PLAN-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO subscription_plans (code, name, price_inr, tier_rank, duration_days, allows_batch, is_active)
                VALUES (?, ?, 14999.00, ?, 180, ?, TRUE)
                """, code, code, tierRank, allowsBatch);
        long planId = jdbcTemplate.queryForObject("SELECT id FROM subscription_plans WHERE code = ?", Long.class, code);
        insertedPlanIds.add(planId);
        return planId;
    }

    private long insertPayment(long userId, long planId, String trackCode, String gatewayOrderId, String amount) {
        jdbcTemplate.update("""
                INSERT INTO payments (user_id, plan_id, track_code, gateway, gateway_order_id, amount, currency, status)
                VALUES (?, ?, ?, 'RAZORPAY', ?, ?, 'INR', 'CREATED')
                """, userId, planId, trackCode, gatewayOrderId, amount);
        long paymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM payments WHERE gateway_order_id = ?", Long.class, gatewayOrderId);
        insertedPaymentIds.add(paymentId);
        return paymentId;
    }

    private long insertBatch(String trackCode, Long planTierMinId, int capacity, long pmUserId) {
        jdbcTemplate.update("""
                INSERT INTO batches (name, track_code, pm_id, plan_tier_min_id, start_date, end_date, capacity, status)
                VALUES (?, ?, ?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 90 DAY, ?, 'PLANNED')
                """, "Batch-" + UUID.randomUUID(), trackCode, pmUserId, planTierMinId, capacity);
        long batchId = jdbcTemplate.queryForObject(
                "SELECT id FROM batches WHERE track_code = ? AND pm_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, trackCode, pmUserId);
        insertedBatchIds.add(batchId);
        return batchId;
    }

    private long currentUserId(String accessToken) {
        String uuid = given()
                .header("Authorization", "Bearer " + accessToken)
            .when()
                .get("/api/v1/users/me")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.uuid");
        return userIdFromUuid(uuid);
    }

    private long userIdFromUuid(String uuid) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE uuid = ?", Long.class, uuid);
    }

    private long registerVerifyAndGetUserId(String fullName) {
        String uuid = registerAndGetUuid(fullName);
        return userIdFromUuid(uuid);
    }

    /** Registers, verifies, and returns the new user's uuid — doesn't log in (most callers only
     * need the id/uuid for setup, not an access token). */
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

    /** ADMIN and HR_MANAGER carry mandatory 2FA (build-plan.md feature 05) — granting either
     * role means the next login returns a setup challenge, not a token pair directly. Same
     * completion flow as {@code TwoFactorFlowIT.adminWithoutTwoFactor_isChallengedWithSetupRequired}. */
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
        grantRole(email, roleCode);

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
        byte[] secret = com.moriah.skillhub.common.util.Base32Codec.decode(secretBase32);

        return given()
                .contentType("application/json")
                .body(Map.of("challengeToken", challengeToken, "totpCode", currentTotpCode(secret)))
            .when()
                .post("/api/v1/auth/2fa/verify")
            .then()
                .statusCode(200)
                .body("data.tokens.accessToken", notNullValue())
                .extract().jsonPath().getString("data.tokens.accessToken");
    }

    /** Minimal local RFC 6238 re-implementation — same reasoning as {@code TwoFactorFlowIT}'s own
     * copy: {@code TotpService} deliberately exposes no code-generation method, only {@code
     * verifyCode}, and this shouldn't be the reason that stops being true. */
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
                .body("data.tokens.accessToken", notNullValue())
                .extract().jsonPath().getString("data.tokens.accessToken");
    }

    private String uniqueEmail(String fullName) {
        return fullName.toLowerCase().replace(" ", ".") + "-" + UUID.randomUUID() + "@example.com";
    }

    /** Same bounded-poll pattern as {@code AuthFlowIT.waitForNotification} — {@code
     * NotificationService.enqueueAfterCommit}'s row lands asynchronously, after this test's own
     * commit. */
    private void waitForNotification(long userId, String templateCode) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM notifications WHERE user_id = ? AND template_code = ?
                    """, Long.class, userId, templateCode);
            if (count != null && count > 0) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while polling for notification", e);
            }
        }
        throw new AssertionError("No " + templateCode + " notification found for user " + userId + " within 10s");
    }

    private void waitForPendingInvoicesToSettle() {
        if (insertedPaymentIds.isEmpty()) {
            return;
        }
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Long pendingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM invoices WHERE payment_id IN (" + placeholders(insertedPaymentIds.size())
                            + ") AND status = 'PENDING'",
                    Long.class, insertedPaymentIds.toArray());
            if (pendingCount == null || pendingCount == 0) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
