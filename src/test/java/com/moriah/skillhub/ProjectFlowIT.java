package com.moriah.skillhub;

import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import com.moriah.skillhub.common.util.Base32Codec;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

/**
 * build-plan.md feature 15 verify line: "A DRAFT cannot attach to a task. Assets return working
 * presigned URLs subject to ownership checks." Real HTTP + JWT auth throughout (same reasoning as
 * {@code AssessmentFlowIT}/{@code AttendanceFlowIT}).
 */
class ProjectFlowIT extends IntegrationTestBase {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3
    };

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void create_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Project Bystander");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("title", "Weather App", "description", "A weather app"))
            .when()
                .post("/api/v1/projects")
            .then()
                .statusCode(403);
    }

    @Test
    void update_asStudent_returns403() {
        String devToken = registerVerifyGrantRoleAndLogin("Update Role Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Update Role Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        String studentToken = registerVerifyAndLogin("Update Role Student");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("description", "hijack attempt"))
            .when()
                .put("/api/v1/projects/" + projectId)
            .then()
                .statusCode(403);
    }

    @Test
    void addAsset_asStudent_returns403() {
        String devToken = registerVerifyGrantRoleAndLogin("Asset Role Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Asset Role Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        String studentToken = registerVerifyAndLogin("Asset Role Student");

        given()
                .header("Authorization", "Bearer " + studentToken)
                .multiPart("assetType", "VIDEO")
                .multiPart("externalUrl", "https://youtu.be/demo")
            .when()
                .post("/api/v1/projects/" + projectId + "/assets")
            .then()
                .statusCode(403);
    }

    @Test
    void addChallenge_asStudent_returns403() {
        String devToken = registerVerifyGrantRoleAndLogin("Challenge Role Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Challenge Role Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        String studentToken = registerVerifyAndLogin("Challenge Role Student");

        given()
                .header("Authorization", "Bearer " + studentToken)
                .multiPart("title", "Fix the crash")
                .multiPart("expectedBehaviour", "Should not divide by zero")
                .multiPart("brokenCode", "broken.py", "def broken(): return 1/0".getBytes(StandardCharsets.UTF_8), "text/plain")
            .when()
                .post("/api/v1/projects/" + projectId + "/challenges")
            .then()
                .statusCode(403);
    }

    @Test
    void publish_asStudent_returns403() {
        String devToken = registerVerifyGrantRoleAndLogin("Publish Role Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Publish Role Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        String studentToken = registerVerifyAndLogin("Publish Role Student");

        given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .post("/api/v1/projects/" + projectId + "/publish")
            .then()
                .statusCode(403);
    }

    @Test
    void create_asDeveloper_savesDraftWithGeneratedSlug() {
        String devToken = registerVerifyGrantRoleAndLogin("Create Dev", "DEVELOPER");

        createProject(devToken, "Weather Dashboard")
                .then()
                .statusCode(201)
                .body("data.status", equalTo("DRAFT"))
                .body("data.slug", equalTo("weather-dashboard"))
                .body("data.assets", hasSize(0))
                .body("data.challenges", hasSize(0));
    }

    @Test
    void list_asStudent_onlySeesPublishedProjects() {
        String devToken = registerVerifyGrantRoleAndLogin("List Dev", "DEVELOPER");
        long draftId = createProject(devToken, "Draft Only " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        long publishedId = createProject(devToken, "Published One " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        publish(devToken, publishedId).then().statusCode(200);

        String studentToken = registerVerifyAndLogin("List Student");
        List<Long> ids = given()
                .header("Authorization", "Bearer " + studentToken)
            .when()
                .get("/api/v1/projects?size=100")
            .then()
                .statusCode(200)
                .extract().jsonPath().getList("data.content.id", Long.class);

        assertThat(ids).contains(publishedId).doesNotContain(draftId);
    }

    @Test
    void list_admin_seesDraftProjectsToo() {
        String devToken = registerVerifyGrantRoleAndLogin("Admin List Dev", "DEVELOPER");
        long draftId = createProject(devToken, "Admin Visible Draft " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");

        String adminToken = registerVerifyGrantRoleAndLogin("List Admin", "ADMIN");
        List<Long> ids = given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/v1/projects?status=DRAFT&size=100")
            .then()
                .statusCode(200)
                .extract().jsonPath().getList("data.content.id", Long.class);

        assertThat(ids).contains(draftId);
    }

    @Test
    void update_byNonCreatorDeveloper_returns403() {
        String ownerToken = registerVerifyGrantRoleAndLogin("Owner Dev", "DEVELOPER");
        long projectId = createProject(ownerToken, "Owned Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        String intruderToken = registerVerifyGrantRoleAndLogin("Intruder Dev", "DEVELOPER");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + intruderToken)
                .body(Map.of("title", "Hijacked"))
            .when()
                .put("/api/v1/projects/" + projectId)
            .then()
                .statusCode(403);
    }

    @Test
    void update_draftProject_editsInPlaceWithoutBumpingVersion() {
        String devToken = registerVerifyGrantRoleAndLogin("Edit Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Editable Draft " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + devToken)
                .body(Map.of("description", "Now with a real description"))
            .when()
                .put("/api/v1/projects/" + projectId)
            .then()
                .statusCode(200)
                .body("data.id", equalTo((int) projectId))
                .body("data.status", equalTo("DRAFT"))
                .body("data.description", equalTo("Now with a real description"));
    }

    @Test
    void update_publishedProjectWithoutVersion_returns400() {
        String devToken = registerVerifyGrantRoleAndLogin("NoVersion Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Needs Version " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        publish(devToken, projectId).then().statusCode(200);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + devToken)
                .body(Map.of("description", "Trying to sneak an edit in"))
            .when()
                .put("/api/v1/projects/" + projectId)
            .then()
                .statusCode(400);
    }

    @Test
    void update_publishedProjectWithVersion_createsNewDraftAndArchivesOld() {
        String devToken = registerVerifyGrantRoleAndLogin("Version Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Versioned Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        publish(devToken, projectId).then().statusCode(200);

        long newVersionId = given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + devToken)
                .body(Map.of("version", "v2", "description", "Second edition"))
            .when()
                .put("/api/v1/projects/" + projectId)
            .then()
                .statusCode(200)
                .body("data.status", equalTo("DRAFT"))
                .body("data.description", equalTo("Second edition"))
                .extract().jsonPath().getLong("data.id");

        assertThat(newVersionId).isNotEqualTo(projectId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM projects WHERE id = ?", String.class, projectId))
                .isEqualTo("ARCHIVED");
    }

    /** build-plan.md: "previous archived, never overwritten" — that invariant only holds if
     * nothing can still attach a new asset to a superseded, archived version after the fact. */
    @Test
    void addAsset_archivedProject_returns409() {
        String devToken = registerVerifyGrantRoleAndLogin("Archived Asset Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Archived Asset Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        publish(devToken, projectId).then().statusCode(200);
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + devToken)
                .body(Map.of("version", "v2"))
            .when()
                .put("/api/v1/projects/" + projectId)
            .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + devToken)
                .multiPart("assetType", "VIDEO")
                .multiPart("externalUrl", "https://youtu.be/demo")
            .when()
                .post("/api/v1/projects/" + projectId + "/assets")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("PROJECT_INVALID_TRANSITION"));
    }

    @Test
    void addAsset_externalUrl_returnsUrlVerbatimWithoutUpload() {
        String devToken = registerVerifyGrantRoleAndLogin("AssetUrl Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Asset Url Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");

        given()
                .header("Authorization", "Bearer " + devToken)
                .multiPart("assetType", "VIDEO")
                .multiPart("externalUrl", "https://youtu.be/demo")
            .when()
                .post("/api/v1/projects/" + projectId + "/assets")
            .then()
                .statusCode(201)
                .body("data.url", equalTo("https://youtu.be/demo"));
    }

    @Test
    void addAsset_realFile_uploadsAndReturnsWorkingPresignedUrl() {
        String devToken = registerVerifyGrantRoleAndLogin("AssetFile Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Asset File Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");

        String url = given()
                .header("Authorization", "Bearer " + devToken)
                .multiPart("assetType", "IMAGE")
                .multiPart("file", "screenshot.png", PNG_BYTES, "image/png")
            .when()
                .post("/api/v1/projects/" + projectId + "/assets")
            .then()
                .statusCode(201)
                .extract().jsonPath().getString("data.url");

        byte[] downloaded = downloadViaPresignedUrl(url);
        assertThat(downloaded).isEqualTo(PNG_BYTES);
    }

    @Test
    void addAsset_neitherFileNorUrl_returns400() {
        String devToken = registerVerifyGrantRoleAndLogin("NoAsset Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Empty Asset Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");

        given()
                .header("Authorization", "Bearer " + devToken)
                .multiPart("assetType", "IMAGE")
            .when()
                .post("/api/v1/projects/" + projectId + "/assets")
            .then()
                .statusCode(400);
    }

    @Test
    void addChallenge_happyPath_returnsWorkingPresignedBrokenCodeUrl() {
        String devToken = registerVerifyGrantRoleAndLogin("Challenge Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Challenge Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        byte[] code = "def broken(): return 1/0".getBytes(StandardCharsets.UTF_8);

        String url = given()
                .header("Authorization", "Bearer " + devToken)
                .multiPart("title", "Fix the crash")
                .multiPart("expectedBehaviour", "Should not divide by zero")
                .multiPart("brokenCode", "broken.py", code, "text/plain")
            .when()
                .post("/api/v1/projects/" + projectId + "/challenges")
            .then()
                .statusCode(201)
                .body("data.testScriptUrl", nullValue())
                .extract().jsonPath().getString("data.brokenCodeUrl");

        byte[] downloaded = downloadViaPresignedUrl(url);
        assertThat(downloaded).isEqualTo(code);
    }

    @Test
    void publish_alreadyPublished_returns409() {
        String devToken = registerVerifyGrantRoleAndLogin("DoublePublish Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Double Publish " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        publish(devToken, projectId).then().statusCode(200);

        publish(devToken, projectId).then().statusCode(409);
    }

    /** The literal verify line: "A DRAFT cannot attach to a task." */
    @Test
    void taskCreate_withDraftProject_returns409() {
        String devToken = registerVerifyGrantRoleAndLogin("TaskDraft Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Task Draft Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");

        String pmToken = registerVerifyGrantRoleAndLogin("TaskDraft PM", "TRAINER_PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("TASK_DRAFT_TRACK", pmUserId);
        long sprintId = insertSprint(batchId);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("sprintId", sprintId, "projectId", projectId, "title", "Fix the widget",
                        "taskType", "BUGFIX"))
            .when()
                .post("/api/v1/tasks")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("PROJECT_NOT_PUBLISHED"));
    }

    @Test
    void taskCreate_withPublishedProject_succeeds() {
        String devToken = registerVerifyGrantRoleAndLogin("TaskPublished Dev", "DEVELOPER");
        long projectId = createProject(devToken, "Task Published Project " + UUID.randomUUID())
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        publish(devToken, projectId).then().statusCode(200);

        String pmToken = registerVerifyGrantRoleAndLogin("TaskPublished PM", "TRAINER_PM");
        long pmUserId = currentUserId(pmToken);
        long batchId = insertBatch("TASK_PUB_TRACK", pmUserId);
        long sprintId = insertSprint(batchId);

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + pmToken)
                .body(Map.of("sprintId", sprintId, "projectId", projectId, "title", "Fix the widget",
                        "taskType", "BUGFIX"))
            .when()
                .post("/api/v1/tasks")
            .then()
                .statusCode(201)
                .body("data.status", equalTo("BACKLOG"));
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    /** Plain {@code RestClient}, not RestAssured — same reasoning {@code UserProfileFlowIT}'s own
     * resume-download test documents: RestAssured's request builder re-encodes whatever URL it's
     * given (String or URI alike), double-encoding the presigned URL's already-percent-encoded
     * AWS SigV4 query string and corrupting the signature. {@code .uri(URI)}, not {@code
     * .uri(String)} — the String overload has the identical problem on {@code RestClient}'s own
     * side too. */
    private byte[] downloadViaPresignedUrl(String url) {
        return RestClient.create().get().uri(URI.create(url)).retrieve().body(byte[].class);
    }

    private Response createProject(String devToken, String title) {
        return given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + devToken)
                .body(Map.of("title", title, "description", "A project"))
            .when()
                .post("/api/v1/projects");
    }

    private Response publish(String devToken, long projectId) {
        return given()
                .header("Authorization", "Bearer " + devToken)
            .when()
                .post("/api/v1/projects/" + projectId + "/publish");
    }

    private long insertBatch(String trackCode, long pmUserId) {
        jdbcTemplate.update("""
                INSERT INTO batches (name, track_code, pm_id, start_date, end_date, capacity, status)
                VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 90 DAY, 20, 'ACTIVE')
                """, "Batch-" + UUID.randomUUID(), trackCode, pmUserId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM batches WHERE track_code = ? AND pm_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, trackCode, pmUserId);
    }

    private long insertSprint(long batchId) {
        jdbcTemplate.update("""
                INSERT INTO sprints (batch_id, sprint_number, start_date, end_date, planned_points, status)
                VALUES (?, 1, CURRENT_DATE, CURRENT_DATE + INTERVAL 7 DAY, 20, 'PLANNED')
                """, batchId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sprints WHERE batch_id = ? ORDER BY id DESC LIMIT 1", Long.class, batchId);
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

        // ADMIN/HR_MANAGER are challenged for mandatory 2FA on every login (feature 05/06
        // `/review` finding) — a plain login() call for these two roles never returns tokens
        // directly, only a challengeToken. Same helper shape as BatchFlowIT/TwoFactorFlowIT.
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

    /** Minimal local RFC 6238 re-implementation — same reasoning as {@code TwoFactorFlowIT}'s/
     * {@code BatchFlowIT}'s own copy: {@code TotpService} deliberately exposes no
     * code-generation method, only {@code verifyCode}. */
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
