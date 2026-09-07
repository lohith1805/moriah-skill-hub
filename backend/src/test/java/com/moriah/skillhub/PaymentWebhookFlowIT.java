package com.moriah.skillhub;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * build-plan.md feature 07 verify line: "Test payment activates a subscription and produces one
 * invoice. Replaying the identical webhook produces no second subscription and no second
 * invoice. A tampered signature returns 400... A refund de-allocates." Real HTTP, real HMAC
 * signatures computed locally with the exact same test webhook secret {@code
 * application-test.yml} configures {@code RazorpayService} with — the same "reimplement the
 * documented protocol independently" pattern {@code TwoFactorFlowIT} uses for TOTP, not a second
 * copy of production code.
 * <p>
 * Stripe's event handling is deliberately not exercised end-to-end here — constructing a
 * correctly-shaped {@code Event}/{@code Session} JSON envelope for Stripe's SDK to deserialize
 * is a much larger surface to get exactly right than Razorpay's flatter payload, and getting it
 * subtly wrong would prove nothing. {@code PaymentWebhookServiceTest} covers Stripe's capture/
 * refund business logic directly, against a mocked {@code Event}; this class additionally proves
 * the Stripe endpoint's signature verification and idempotency wiring with a minimal,
 * unrecognized-event-type payload, which needs no typed object deserialization at all.
 */
class PaymentWebhookFlowIT extends IntegrationTestBase {

    private static final String RAZORPAY_WEBHOOK_SECRET = "test-razorpay-webhook-secret-never-reused-anywhere-else";
    private static final String STRIPE_WEBHOOK_SECRET = "test-stripe-webhook-secret-never-reused-anywhere-else";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Real HTTP means this class can't use @Transactional rollback (see class Javadoc) — its
    // inserted rows are genuinely committed, so left alone they leak into the shared container
    // and break other ITs' exact-count assertions (e.g. SeedIdempotencyIT's subscription_plans
    // count). Tracked here and deleted in cleanUpLeakedFixtureRows(), in FK-dependency order.
    // Users are deliberately left in place — audit_logs.user_id is FK RESTRICT with no cascade,
    // and no other IT asserts an exact users count, so deleting them isn't worth the risk.
    private final List<Long> insertedPaymentIds = new ArrayList<>();
    private final List<Long> insertedPlanIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @AfterEach
    void cleanUpLeakedFixtureRows() {
        waitForPendingInvoicesToSettle();
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
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    /** {@code InvoiceGenerationJob} runs {@code @Async} after commit — a test that doesn't itself
     * wait for it (only {@code razorpayCapture_activatesSubscriptionAndProducesOneInvoice} does,
     * via {@link #assertInvoiceEventuallyIssued}) can return before that job's UPDATE lands.
     * Deleting the invoice row out from under it in cleanup would then throw Hibernate's {@code
     * StaleStateException} on the background thread — wait briefly for any still-PENDING invoice
     * belonging to this test's own payments to settle before cleanup deletes anything. */
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

    @Test
    void razorpayCapture_activatesSubscriptionAndProducesOneInvoice() {
        long userId = insertUser("razorpay-capture-" + UUID.randomUUID() + "@example.com");
        long planId = insertPlan(30);
        String orderId = "order_" + UUID.randomUUID();
        long paymentId = insertPayment(userId, planId, orderId, "14999.00");
        String gatewayPaymentId = "pay_" + UUID.randomUUID();

        String body = razorpayCapturedPayload(orderId, gatewayPaymentId, 1499900);
        given()
                .header("X-Razorpay-Signature", razorpaySignature(body))
                .contentType("application/json")
                .body(body)
            .when()
                .post("/api/v1/webhooks/razorpay")
            .then()
                .statusCode(200);

        assertPaymentStatus(paymentId, "CAPTURED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT gateway_payment_id FROM payments WHERE id = ?", String.class, paymentId))
                .isEqualTo(gatewayPaymentId);

        Long subscriptionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_subscriptions WHERE user_id = ? AND status = 'ACTIVE'
                """, Long.class, userId);
        assertThat(subscriptionCount).isEqualTo(1);

        Long invoiceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE payment_id = ?", Long.class, paymentId);
        assertThat(invoiceCount).isEqualTo(1);

        // InvoiceGenerationJob runs @Async after commit — poll briefly rather than assuming
        // instant completion, but this is genuinely proving the async path runs end to end, not
        // just that the PENDING row was created synchronously. No Awaitility (not an approved
        // dependency, code-standards.md "Dependencies") — a bounded manual poll is simpler than
        // adding a library for one usage.
        assertInvoiceEventuallyIssued(paymentId);

        // Feature 08 clears the "PDF written locally, not to S3" stub — the invoice now really
        // uploads to (test) MinIO via StorageService, not local disk; pdf_key is the proof.
        String pdfKey = jdbcTemplate.queryForObject(
                "SELECT pdf_key FROM invoices WHERE payment_id = ?", String.class, paymentId);
        assertThat(pdfKey).startsWith("invoices/").endsWith(".pdf");
    }

    @Test
    void razorpayCapture_replayedWebhook_doesNotDuplicateSubscriptionOrInvoice() {
        long userId = insertUser("razorpay-replay-" + UUID.randomUUID() + "@example.com");
        long planId = insertPlan(30);
        String orderId = "order_" + UUID.randomUUID();
        long paymentId = insertPayment(userId, planId, orderId, "14999.00");
        String gatewayPaymentId = "pay_" + UUID.randomUUID();

        String body = razorpayCapturedPayload(orderId, gatewayPaymentId, 1499900);
        String signature = razorpaySignature(body);

        for (int i = 0; i < 2; i++) {
            given()
                    .header("X-Razorpay-Signature", signature)
                    .contentType("application/json")
                    .body(body)
                .when()
                    .post("/api/v1/webhooks/razorpay")
                .then()
                    .statusCode(200);
        }

        assertPaymentStatus(paymentId, "CAPTURED");
        Long subscriptionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_subscriptions WHERE user_id = ?", Long.class, userId);
        assertThat(subscriptionCount).isEqualTo(1);
        Long invoiceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE payment_id = ?", Long.class, paymentId);
        assertThat(invoiceCount).isEqualTo(1);

        // Scoped to this test's own payload marker, not a class-wide count — same fix as
        // tamperedSignature_...'s assertion below. Feature 10's BatchFlowIT now also fires real
        // Razorpay webhooks against this same shared static container, so an unscoped COUNT(*)
        // here counts its rows too, not just this class's.
        Long webhookEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_events WHERE gateway = 'RAZORPAY' AND payload LIKE ?",
                Long.class, "%" + gatewayPaymentId + "%");
        assertThat(webhookEventCount).isEqualTo(1);
    }

    @Test
    void tamperedSignature_returns400_andNeverClaimsOrProcessesTheEvent() {
        String body = razorpayCapturedPayload("order_nonexistent", "pay_nonexistent", 100000);

        given()
                .header("X-Razorpay-Signature", "0000000000000000000000000000000000000000000000000000000000000000")
                .contentType("application/json")
                .body(body)
            .when()
                .post("/api/v1/webhooks/razorpay")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("INVALID_WEBHOOK_SIGNATURE"));

        // Scoped to this test's own payload marker, not a class-wide count — other test methods
        // in this class commit their own RAZORPAY webhook_events rows (no @Transactional
        // rollback here, see class Javadoc), so an unscoped COUNT(*) sees their rows too.
        Long webhookEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_events WHERE gateway = 'RAZORPAY' AND payload LIKE '%pay_nonexistent%'",
                Long.class);
        assertThat(webhookEventCount).isEqualTo(0);
    }

    @Test
    void razorpayRefund_cancelsSubscriptionAndMarksPaymentRefunded() {
        long userId = insertUser("razorpay-refund-" + UUID.randomUUID() + "@example.com");
        long planId = insertPlan(30);
        String orderId = "order_" + UUID.randomUUID();
        long paymentId = insertPayment(userId, planId, orderId, "14999.00");
        String gatewayPaymentId = "pay_" + UUID.randomUUID();

        String captureBody = razorpayCapturedPayload(orderId, gatewayPaymentId, 1499900);
        given()
                .header("X-Razorpay-Signature", razorpaySignature(captureBody))
                .contentType("application/json")
                .body(captureBody)
            .when()
                .post("/api/v1/webhooks/razorpay")
            .then()
                .statusCode(200);
        assertPaymentStatus(paymentId, "CAPTURED");

        String refundBody = razorpayRefundPayload(gatewayPaymentId);
        given()
                .header("X-Razorpay-Signature", razorpaySignature(refundBody))
                .contentType("application/json")
                .body(refundBody)
            .when()
                .post("/api/v1/webhooks/razorpay")
            .then()
                .statusCode(200);

        assertPaymentStatus(paymentId, "REFUNDED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM user_subscriptions WHERE user_id = ? AND payment_id = ?
                """, String.class, userId, paymentId))
                .isEqualTo("CANCELLED");
    }

    @Test
    void amountMismatch_marksPaymentFailed_neverActivatesAnything() {
        long userId = insertUser("razorpay-mismatch-" + UUID.randomUUID() + "@example.com");
        long planId = insertPlan(30);
        String orderId = "order_" + UUID.randomUUID();
        long paymentId = insertPayment(userId, planId, orderId, "14999.00");

        // Gateway reports a different amount than what checkout actually computed — the exact
        // scenario "never trust the amount in the webhook payload" (library-docs.md) guards.
        String body = razorpayCapturedPayload(orderId, "pay_" + UUID.randomUUID(), 100);
        given()
                .header("X-Razorpay-Signature", razorpaySignature(body))
                .contentType("application/json")
                .body(body)
            .when()
                .post("/api/v1/webhooks/razorpay")
            .then()
                .statusCode(200); // still 200 to the gateway — this is our problem, not theirs

        assertPaymentStatus(paymentId, "FAILED");
        Long subscriptionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_subscriptions WHERE user_id = ?", Long.class, userId);
        assertThat(subscriptionCount).isEqualTo(0);
    }

    @Test
    void stripeUnrecognizedEventType_verifiesSignatureAndClaimsIdempotently_withoutProcessing() {
        String eventId = "evt_" + UUID.randomUUID();
        String body = """
                {"id":"%s","type":"invoice.created","data":{"object":{}}}
                """.formatted(eventId);

        given()
                .header("Stripe-Signature", stripeSignature(body))
                .contentType("application/json")
                .body(body)
            .when()
                .post("/api/v1/webhooks/stripe")
            .then()
                .statusCode(200);

        // Idempotently claimed — replaying the exact same event is still a no-op 200, not a
        // second webhook_events row.
        given()
                .header("Stripe-Signature", stripeSignature(body))
                .contentType("application/json")
                .body(body)
            .when()
                .post("/api/v1/webhooks/stripe")
            .then()
                .statusCode(200);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_events WHERE gateway = 'STRIPE' AND event_id = ?",
                Long.class, eventId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void stripeTamperedSignature_returns400() {
        String body = """
                {"id":"evt_tampered","type":"invoice.created","data":{"object":{}}}
                """;

        given()
                .header("Stripe-Signature", "t=1,v1=0000000000000000000000000000000000000000000000000000000000000000")
                .contentType("application/json")
                .body(body)
            .when()
                .post("/api/v1/webhooks/stripe")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("INVALID_WEBHOOK_SIGNATURE"));
    }

    /** Polls up to 10s for {@code InvoiceGenerationJob}'s {@code @Async} listener to finish —
     * see the class Javadoc for why this isn't Awaitility. */
    private void assertInvoiceEventuallyIssued(long paymentId) {
        long deadline = System.currentTimeMillis() + 10_000;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            status = jdbcTemplate.queryForObject(
                    "SELECT status FROM invoices WHERE payment_id = ?", String.class, paymentId);
            if ("ISSUED".equals(status)) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while polling for invoice status", e);
            }
        }
        assertThat(status).isEqualTo("ISSUED");
    }

    private void assertPaymentStatus(long paymentId, String expectedStatus) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE id = ?", String.class, paymentId))
                .isEqualTo(expectedStatus);
    }

    private long insertUser(String email) {
        jdbcTemplate.update("""
                INSERT INTO users (uuid, full_name, email, status)
                VALUES (UUID(), 'Webhook Test User', ?, 'ACTIVE')
                """, email);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertPlan(int durationDays) {
        // subscription_plans.code is VARCHAR(30) — a full UUID doesn't fit, an 8-char slice does.
        String code = "PLAN-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO subscription_plans (code, name, price_inr, tier_rank, duration_days, is_active)
                VALUES (?, ?, 14999.00, 3, ?, TRUE)
                """, code, code, durationDays);
        long planId = jdbcTemplate.queryForObject("SELECT id FROM subscription_plans WHERE code = ?", Long.class, code);
        insertedPlanIds.add(planId);
        return planId;
    }

    private long insertPayment(long userId, long planId, String gatewayOrderId, String amount) {
        jdbcTemplate.update("""
                INSERT INTO payments (user_id, plan_id, gateway, gateway_order_id, amount, currency, status)
                VALUES (?, ?, 'RAZORPAY', ?, ?, 'INR', 'CREATED')
                """, userId, planId, gatewayOrderId, amount);
        long paymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM payments WHERE gateway_order_id = ?", Long.class, gatewayOrderId);
        insertedPaymentIds.add(paymentId);
        return paymentId;
    }

    private String razorpayCapturedPayload(String orderId, String paymentId, long amountPaise) {
        return """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"%s","order_id":"%s","amount":%d,"currency":"INR","status":"captured"}}}}
                """.formatted(paymentId, orderId, amountPaise);
    }

    private String razorpayRefundPayload(String gatewayPaymentId) {
        return """
                {"event":"refund.processed","payload":{"payment":{"entity":{"id":"%s"}},"refund":{"entity":{"id":"rfnd_%s"}}}}
                """.formatted(gatewayPaymentId, UUID.randomUUID());
    }

    /** Razorpay's own scheme: hex-encoded HMAC-SHA256 of the raw body, keyed by the webhook
     * secret — {@code Utils.verifyWebhookSignature} verifies exactly this. */
    private String razorpaySignature(String body) {
        return hmacHex(body, RAZORPAY_WEBHOOK_SECRET, "HmacSHA256");
    }

    /** Stripe's documented scheme: {@code t=<unix seconds>,v1=<hex HMAC-SHA256 of "<t>.<body>">}
     * — {@code Webhook.constructEvent} parses this exact format. */
    private String stripeSignature(String body) {
        long timestamp = System.currentTimeMillis() / 1000;
        String signedPayload = timestamp + "." + body;
        String signature = hmacHex(signedPayload, STRIPE_WEBHOOK_SECRET, "HmacSHA256");
        return "t=" + timestamp + ",v1=" + signature;
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
}
