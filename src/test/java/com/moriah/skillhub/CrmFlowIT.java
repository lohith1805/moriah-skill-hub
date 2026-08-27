package com.moriah.skillhub;

import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import io.restassured.RestAssured;
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
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * build-plan.md feature 18's own "Verify" line, exercised end to end over real HTTP: "Same email
 * and phone twice yields one lead and two activities. Skipping a stage returns 409." Plus the
 * role-restriction and webhook-signature coverage code-standards.md's Testing section requires
 * for every feature — every one of LeadController's 5 {@code @PreAuthorize} endpoints has its own
 * wrong-role {@code 403} test below, not just {@code create}.
 */
class CrmFlowIT extends IntegrationTestBase {

    private static final String WHATSAPP_APP_SECRET = "test-whatsapp-app-secret";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void create_sameEmailAndPhoneTwice_yieldsOneLeadAndTwoActivities() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm Dedup Agent", "LEAD_GEN");
        Map<String, Object> body = Map.of(
                "name", "Prospective Student",
                "email", "prospect-" + UUID.randomUUID() + "@example.com",
                "phone", "+91 90000 11111",
                "source", "LANDING_PAGE",
                "leadType", "INDIVIDUAL");

        long leadId = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(body)
            .when()
                .post("/api/v1/leads")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(body)
            .when()
                .post("/api/v1/leads")
            .then()
                .statusCode(201)
                .body("data.id", equalTo((int) leadId));

        Integer leadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM leads WHERE id = ?", Integer.class, leadId);
        assertThat(leadCount).isEqualTo(1);

        // One LEAD_CREATED activity from the first submission, one DUPLICATE_SUBMISSION activity
        // from the second — the literal build-plan.md Verify line.
        Integer activityCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lead_activities WHERE lead_id = ?", Integer.class, leadId);
        assertThat(activityCount).isEqualTo(2);
    }

    @Test
    void create_selfAssignsTheCreatingAgent() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm SelfAssign Agent", "LEAD_GEN");

        String assignedAgentUuid = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(Map.of(
                        "name", "Self Assign Prospect",
                        "email", "selfassign-" + UUID.randomUUID() + "@example.com",
                        "phone", "+1 555 222 3333",
                        "source", "REFERRAL",
                        "leadType", "INDIVIDUAL"))
            .when()
                .post("/api/v1/leads")
            .then()
                .statusCode(201)
                .extract().jsonPath().getString("data.assignedAgentUuid");

        assertThat(assignedAgentUuid).isNotBlank();

        given()
                .header("Authorization", "Bearer " + agentToken)
                .queryParam("agentUuid", assignedAgentUuid)
            .when()
                .get("/api/v1/leads")
            .then()
                .statusCode(200)
                .body("data.content.size()", equalTo(1));
    }

    @Test
    void create_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Crm Forbidden Student");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of(
                        "name", "X", "email", "x-" + UUID.randomUUID() + "@example.com",
                        "phone", "+1 555 000 0000", "source", "REFERRAL", "leadType", "INDIVIDUAL"))
            .when()
                .post("/api/v1/leads")
            .then()
                .statusCode(403);
    }

    @Test
    void list_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Crm List Forbidden Student");

        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .get("/api/v1/leads")
            .then()
                .statusCode(403);
    }

    @Test
    void updateStatus_skippingAStage_returns409() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm Skip Agent", "LEAD_GEN");
        long leadId = createLead(agentToken, "skip");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(Map.of("newStatus", "DEMO_SCHEDULED"))
            .when()
                .put("/api/v1/leads/" + leadId + "/status")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("LEAD_PIPELINE_SKIP"));
    }

    @Test
    void updateStatus_asStudent_returns403() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm Status Owner Agent", "LEAD_GEN");
        String studentToken = registerVerifyAndLogin("Crm Status Student");
        long leadId = createLead(agentToken, "status-403");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("newStatus", "CONTACTED"))
            .when()
                .put("/api/v1/leads/" + leadId + "/status")
            .then()
                .statusCode(403);
    }

    @Test
    void updateStatus_backwardWithReason_succeeds() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm Backward Agent", "LEAD_GEN");
        long leadId = createLead(agentToken, "backward");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(Map.of("newStatus", "CONTACTED"))
            .when()
                .put("/api/v1/leads/" + leadId + "/status")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("CONTACTED"));

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(Map.of("newStatus", "NEW", "reason", "Contacted the wrong number, resetting."))
            .when()
                .put("/api/v1/leads/" + leadId + "/status")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("NEW"));
    }

    @Test
    void updateStatus_backwardWithoutReason_returns409() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm No Reason Agent", "LEAD_GEN");
        long leadId = createLead(agentToken, "backward-no-reason");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(Map.of("newStatus", "CONTACTED"))
            .when()
                .put("/api/v1/leads/" + leadId + "/status")
            .then()
                .statusCode(200);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(Map.of("newStatus", "NEW"))
            .when()
                .put("/api/v1/leads/" + leadId + "/status")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("LEAD_BACKWARD_REASON_REQUIRED"));
    }

    @Test
    void updateStatus_toEnrolledWithUnknownConvertedUserUuid_returns404() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm Enroll Agent", "LEAD_GEN");
        long leadId = createLead(agentToken, "enroll-404");
        advanceStatus(agentToken, leadId, "CONTACTED", null);
        advanceStatus(agentToken, leadId, "DEMO_SCHEDULED", null);
        advanceStatus(agentToken, leadId, "COUNSELLING_DONE", null);
        advanceStatus(agentToken, leadId, "PAYMENT_PENDING", null);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(Map.of("newStatus", "ENROLLED", "convertedUserUuid", UUID.randomUUID().toString()))
            .when()
                .put("/api/v1/leads/" + leadId + "/status")
            .then()
                .statusCode(404)
                .body("error.code", equalTo("USER_NOT_FOUND"));
    }

    @Test
    void addActivity_happyPath_returns201AndListsUnderTheLead() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm Activity Agent", "LEAD_GEN");
        long leadId = createLead(agentToken, "activity");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(Map.of(
                        "activityType", "CALL",
                        "outcome", "INTERESTED",
                        "notes", "Wants a demo next week.",
                        "occurredAt", Instant.now().toString()))
            .when()
                .post("/api/v1/leads/" + leadId + "/activities")
            .then()
                .statusCode(201)
                .body("data.activityType", equalTo("CALL"));

        // 1 LEAD_CREATED (at create time) + 1 CALL just logged.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lead_activities WHERE lead_id = ?", Integer.class, leadId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void addActivity_whatsAppWithTemplateCode_returns201() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm Whatsapp Send Agent", "LEAD_GEN");
        long leadId = createLead(agentToken, "whatsapp-activity");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(Map.of(
                        "activityType", "WHATSAPP",
                        "outcome", "SENT",
                        "occurredAt", Instant.now().toString(),
                        "templateCode", "demo_followup_v1"))
            .when()
                .post("/api/v1/leads/" + leadId + "/activities")
            .then()
                .statusCode(201)
                .body("data.activityType", equalTo("WHATSAPP"));
    }

    @Test
    void addActivity_whatsAppWithoutTemplateCode_returns400() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm Whatsapp Agent", "LEAD_GEN");
        long leadId = createLead(agentToken, "whatsapp-no-template");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(Map.of(
                        "activityType", "WHATSAPP",
                        "occurredAt", Instant.now().toString()))
            .when()
                .post("/api/v1/leads/" + leadId + "/activities")
            .then()
                .statusCode(400);
    }

    @Test
    void addActivity_asStudent_returns403() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm Activity Owner Agent", "LEAD_GEN");
        String studentToken = registerVerifyAndLogin("Crm Activity Student");
        long leadId = createLead(agentToken, "activity-403");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("activityType", "CALL", "occurredAt", Instant.now().toString()))
            .when()
                .post("/api/v1/leads/" + leadId + "/activities")
            .then()
                .statusCode(403);
    }

    @Test
    void myTargets_noneSetForCurrentMonth_returns404() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm No Target Agent", "LEAD_GEN");

        given()
                .header("Authorization", "Bearer " + agentToken)
            .when()
                .get("/api/v1/leads/targets/me")
            .then()
                .statusCode(404)
                .body("error.code", equalTo("SALES_TARGET_NOT_FOUND"));
    }

    @Test
    void myTargets_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Crm Targets Student");

        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .get("/api/v1/leads/targets/me")
            .then()
                .statusCode(403);
    }

    @Test
    void whatsappWebhook_validSignatureMatchingLead_logsInboundActivity() {
        String agentToken = registerVerifyGrantRoleAndLogin("Crm Webhook Agent", "LEAD_GEN");
        String phone = "919000022222";
        long leadId = createLeadWithPhone(agentToken, "webhook", "+91 " + phone.substring(2));

        String messageId = "wamid." + UUID.randomUUID();
        String body = """
                {"object":"whatsapp_business_account","entry":[{"id":"e1","changes":[{"value":{\
                "messaging_product":"whatsapp","messages":[{"from":"%s","id":"%s",\
                "timestamp":"%d","type":"text","text":{"body":"Yes, please call me back"}}]},\
                "field":"messages"}]}]}\
                """.formatted(phone, messageId, Instant.now().getEpochSecond());

        given()
                .contentType("application/json")
                .header("X-Hub-Signature-256", "sha256=" + hmacSha256Hex(body, WHATSAPP_APP_SECRET))
                .body(body)
            .when()
                .post("/api/v1/webhooks/whatsapp")
            .then()
                .statusCode(200);

        Integer inboundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lead_activities WHERE lead_id = ? AND activity_type = 'WHATSAPP_INBOUND'",
                Integer.class, leadId);
        assertThat(inboundCount).isEqualTo(1);
    }

    @Test
    void whatsappWebhook_invalidSignature_returns400() {
        String body = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}";

        given()
                .contentType("application/json")
                .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64))
                .body(body)
            .when()
                .post("/api/v1/webhooks/whatsapp")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("INVALID_WEBHOOK_SIGNATURE"));
    }

    @Test
    void whatsappWebhook_missingSignatureHeader_returns400NotServerError() {
        given()
                .contentType("application/json")
                .body("{\"object\":\"whatsapp_business_account\",\"entry\":[]}")
            .when()
                .post("/api/v1/webhooks/whatsapp")
            .then()
                .statusCode(400)
                .body("error.code", equalTo("INVALID_WEBHOOK_SIGNATURE"));
    }

    private String hmacSha256Hex(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void advanceStatus(String agentToken, long leadId, String newStatus, String reason) {
        Map<String, Object> body = reason == null
                ? Map.of("newStatus", newStatus)
                : Map.of("newStatus", newStatus, "reason", reason);
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(body)
            .when()
                .put("/api/v1/leads/" + leadId + "/status")
            .then()
                .statusCode(200);
    }

    private long createLead(String agentToken, String tag) {
        return createLeadWithPhone(agentToken, tag, "+1 555 " + UUID.randomUUID().toString().substring(0, 7));
    }

    private long createLeadWithPhone(String agentToken, String tag, String phone) {
        return given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + agentToken)
                .body(Map.of(
                        "name", "Lead " + tag,
                        "email", "lead-" + tag + "-" + UUID.randomUUID() + "@example.com",
                        "phone", phone,
                        "source", "LANDING_PAGE",
                        "leadType", "INDIVIDUAL"))
            .when()
                .post("/api/v1/leads")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");
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
}
