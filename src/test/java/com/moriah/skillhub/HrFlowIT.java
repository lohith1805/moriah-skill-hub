package com.moriah.skillhub;

import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import com.moriah.skillhub.common.util.Base32Codec;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
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

/**
 * build-plan.md feature 19's own "Verify" line, exercised end to end over real HTTP:
 * "Regenerating a month returns 409. Overlapping leave rejected. A terminated student cannot
 * receive an experience letter." Plus wrong-role {@code 403} coverage for every one of the 5
 * HR controllers' role-restricted endpoints.
 */
class HrFlowIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // --- employees ---

    @Test
    void createEmployee_asHrManager_returns201() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Create Manager", "HR_MANAGER");
        String targetUuid = registerVerifyAndLoginReturningUuid("Hr Create Target");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of(
                        "userUuid", targetUuid,
                        "employeeCode", "EMP-" + UUID.randomUUID().toString().substring(0, 8),
                        "department", "Engineering",
                        "designation", "Trainer",
                        "employmentType", "FULL_TIME",
                        "dateOfJoining", "2026-01-01",
                        "baseSalary", 50000.00))
            .when()
                .post("/api/v1/hr/employees")
            .then()
                .statusCode(201)
                .body("data.userUuid", equalTo(targetUuid));
    }

    @Test
    void createEmployee_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Hr Create Forbidden");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of(
                        "userUuid", UUID.randomUUID().toString(),
                        "employeeCode", "EMP-X", "department", "Eng", "designation", "Trainer",
                        "employmentType", "FULL_TIME", "dateOfJoining", "2026-01-01", "baseSalary", 1000.00))
            .when()
                .post("/api/v1/hr/employees")
            .then()
                .statusCode(403);
    }

    @Test
    void createEmployee_duplicateUser_returns409() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Dup Manager", "HR_MANAGER");
        String targetUuid = registerVerifyAndLoginReturningUuid("Hr Dup Target");
        Map<String, Object> body = Map.of(
                "userUuid", targetUuid, "employeeCode", "EMP-" + UUID.randomUUID().toString().substring(0, 8),
                "department", "Eng", "designation", "Trainer", "employmentType", "FULL_TIME",
                "dateOfJoining", "2026-01-01", "baseSalary", 1000.00);

        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + hrToken).body(body)
                .when().post("/api/v1/hr/employees")
                .then().statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("userUuid", targetUuid, "employeeCode", "EMP-" + UUID.randomUUID().toString().substring(0, 8),
                        "department", "Eng", "designation", "Trainer", "employmentType", "FULL_TIME",
                        "dateOfJoining", "2026-01-01", "baseSalary", 1000.00))
            .when()
                .post("/api/v1/hr/employees")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("EMPLOYEE_ALREADY_EXISTS"));
    }

    // --- documents ---

    @Test
    void uploadAndVerifyDocument_happyPath() {
        String studentToken = registerVerifyAndLogin("Hr Doc Student");
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Doc Manager", "HR_MANAGER");

        byte[] pdfBytes = minimalPdfBytes();
        long documentId = given()
                .header("Authorization", "Bearer " + studentToken)
                .contentType("multipart/form-data")
                .multiPart("file", "id.pdf", pdfBytes, "application/pdf")
                .multiPart("documentType", "AADHAAR")
            .when()
                .post("/api/v1/hr/documents")
            .then()
                .statusCode(201)
                .body("data.verificationStatus", equalTo("PENDING"))
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("decision", "VERIFIED"))
            .when()
                .put("/api/v1/hr/documents/" + documentId + "/verify")
            .then()
                .statusCode(200)
                .body("data.verificationStatus", equalTo("VERIFIED"));
    }

    @Test
    void verifyDocument_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Hr Doc Forbidden Student");
        byte[] pdfBytes = minimalPdfBytes();
        long documentId = given()
                .header("Authorization", "Bearer " + studentToken)
                .contentType("multipart/form-data")
                .multiPart("file", "id.pdf", pdfBytes, "application/pdf")
                .multiPart("documentType", "AADHAAR")
            .when()
                .post("/api/v1/hr/documents")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("decision", "VERIFIED"))
            .when()
                .put("/api/v1/hr/documents/" + documentId + "/verify")
            .then()
                .statusCode(403);
    }

    @Test
    void verifyDocument_hrManagerOwnDocument_returns403() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Doc Self Manager", "HR_MANAGER");
        byte[] pdfBytes = minimalPdfBytes();
        long documentId = given()
                .header("Authorization", "Bearer " + hrToken)
                .contentType("multipart/form-data")
                .multiPart("file", "id.pdf", pdfBytes, "application/pdf")
                .multiPart("documentType", "AADHAAR")
            .when()
                .post("/api/v1/hr/documents")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("decision", "VERIFIED"))
            .when()
                .put("/api/v1/hr/documents/" + documentId + "/verify")
            .then()
                .statusCode(403)
                .body("error.code", equalTo("SELF_DECISION_NOT_ALLOWED"));
    }

    @Test
    void verifyDocument_alreadyDecided_returns409() {
        String studentToken = registerVerifyAndLogin("Hr Doc Redecide Student");
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Doc Redecide Manager", "HR_MANAGER");
        byte[] pdfBytes = minimalPdfBytes();
        long documentId = given()
                .header("Authorization", "Bearer " + studentToken)
                .contentType("multipart/form-data")
                .multiPart("file", "id.pdf", pdfBytes, "application/pdf")
                .multiPart("documentType", "AADHAAR")
            .when()
                .post("/api/v1/hr/documents")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + hrToken)
                .body(Map.of("decision", "VERIFIED"))
                .when().put("/api/v1/hr/documents/" + documentId + "/verify")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("decision", "REJECTED", "rejectionReason", "Changed my mind"))
            .when()
                .put("/api/v1/hr/documents/" + documentId + "/verify")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("HR_DOCUMENT_ALREADY_DECIDED"));
    }

    // --- leaves ---

    @Test
    void createAndApproveLeave_happyPath() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Leave Manager", "HR_MANAGER");
        String employeeToken = registerVerifyAndLogin("Hr Leave Employee");
        createEmployeeFor(hrToken, employeeToken, "EMP-" + UUID.randomUUID().toString().substring(0, 8));

        long leaveId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + employeeToken)
                .body(Map.of("leaveType", "SICK", "fromDate", "2026-04-01", "toDate", "2026-04-02", "reason", "Fever"))
            .when()
                .post("/api/v1/hr/leaves")
            .then()
                .statusCode(201)
                .body("data.days", equalTo(2))
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("decision", "APPROVED"))
            .when()
                .put("/api/v1/hr/leaves/" + leaveId + "/decision")
            .then()
                .statusCode(200)
                .body("data.status", equalTo("APPROVED"));
    }

    @Test
    void approveLeave_overlappingAlreadyApprovedLeave_returns409() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Overlap Manager", "HR_MANAGER");
        String employeeToken = registerVerifyAndLogin("Hr Overlap Employee");
        createEmployeeFor(hrToken, employeeToken, "EMP-" + UUID.randomUUID().toString().substring(0, 8));

        long firstLeaveId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + employeeToken)
                .body(Map.of("leaveType", "CASUAL", "fromDate", "2026-05-01", "toDate", "2026-05-05", "reason", "Trip"))
            .when()
                .post("/api/v1/hr/leaves")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + hrToken)
                .body(Map.of("decision", "APPROVED"))
                .when().put("/api/v1/hr/leaves/" + firstLeaveId + "/decision")
                .then().statusCode(200);

        long secondLeaveId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + employeeToken)
                .body(Map.of("leaveType", "SICK", "fromDate", "2026-05-03", "toDate", "2026-05-04", "reason", "Overlap"))
            .when()
                .post("/api/v1/hr/leaves")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("decision", "APPROVED"))
            .when()
                .put("/api/v1/hr/leaves/" + secondLeaveId + "/decision")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("LEAVE_OVERLAPS_APPROVED_LEAVE"));
    }

    @Test
    void decideLeave_hrManagerOwnRequest_returns403() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Leave Self Manager", "HR_MANAGER");
        createEmployeeFor(hrToken, hrToken, "EMP-" + UUID.randomUUID().toString().substring(0, 8));

        long leaveId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("leaveType", "SICK", "fromDate", "2026-07-01", "toDate", "2026-07-01", "reason", "Fever"))
            .when()
                .post("/api/v1/hr/leaves")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("decision", "APPROVED"))
            .when()
                .put("/api/v1/hr/leaves/" + leaveId + "/decision")
            .then()
                .statusCode(403)
                .body("error.code", equalTo("SELF_DECISION_NOT_ALLOWED"));
    }

    @Test
    void decideLeave_asUnrelatedEmployee_returns403() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Unrelated Manager", "HR_MANAGER");
        String employeeToken = registerVerifyAndLogin("Hr Unrelated Employee");
        createEmployeeFor(hrToken, employeeToken, "EMP-" + UUID.randomUUID().toString().substring(0, 8));
        String otherToken = registerVerifyAndLogin("Hr Unrelated Other");

        long leaveId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + employeeToken)
                .body(Map.of("leaveType", "EARNED", "fromDate", "2026-06-01", "toDate", "2026-06-02", "reason", "R"))
            .when()
                .post("/api/v1/hr/leaves")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + otherToken)
                .body(Map.of("decision", "APPROVED"))
            .when()
                .put("/api/v1/hr/leaves/" + leaveId + "/decision")
            .then()
                .statusCode(403);
    }

    // --- payroll ---

    @Test
    void generatePayroll_regeneratingSameMonth_returns409() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Payroll Manager", "HR_MANAGER");
        String employeeToken = registerVerifyAndLogin("Hr Payroll Employee");
        long employeeId = createEmployeeFor(hrToken, employeeToken, "EMP-" + UUID.randomUUID().toString().substring(0, 8));

        Map<String, Object> generateBody = Map.of(
                "periodMonth", "2026-04-01",
                "workingDays", 22,
                "lines", java.util.List.of(Map.of("employeeId", employeeId, "presentDays", 22, "deductions", 0)));

        given().contentType(ContentType.JSON).header("Authorization", "Bearer " + hrToken).body(generateBody)
                .when().post("/api/v1/hr/payroll/generate")
                .then().statusCode(201)
                .body("data[0].status", equalTo("FINALISED"));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(generateBody)
            .when()
                .post("/api/v1/hr/payroll/generate")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("PAYROLL_ALREADY_GENERATED"));
    }

    @Test
    void generatePayroll_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Hr Payroll Forbidden");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("periodMonth", "2026-04-01", "workingDays", 22,
                        "lines", java.util.List.of(Map.of("employeeId", 1, "presentDays", 22))))
            .when()
                .post("/api/v1/hr/payroll/generate")
            .then()
                .statusCode(403);
    }

    @Test
    void listPayroll_asHrManager_returnsGeneratedRecordsWithDownloadUrl() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Payroll List Manager", "HR_MANAGER");
        String employeeToken = registerVerifyAndLogin("Hr Payroll List Employee");
        long employeeId = createEmployeeFor(hrToken, employeeToken, "EMP-" + UUID.randomUUID().toString().substring(0, 8));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("periodMonth", "2026-05-01", "workingDays", 20,
                        "lines", java.util.List.of(Map.of("employeeId", employeeId, "presentDays", 20, "deductions", 500.00))))
            .when()
                .post("/api/v1/hr/payroll/generate")
            .then()
                .statusCode(201);

        given()
                .header("Authorization", "Bearer " + hrToken)
                .queryParam("month", "2026-05-01")
            .when()
                .get("/api/v1/hr/payroll")
            .then()
                .statusCode(200)
                .body("data.content.size()", equalTo(1))
                .body("data.content[0].payslipDownloadUrl", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void listPayroll_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Hr Payroll List Forbidden");

        given()
                .header("Authorization", "Bearer " + studentToken)
                .queryParam("month", "2026-05-01")
            .when()
                .get("/api/v1/hr/payroll")
            .then()
                .statusCode(403);
    }

    // --- letters ---

    @Test
    void issueOfferLetter_noEligibilityCheck_returns200() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Letter Offer Manager", "HR_MANAGER");
        String targetUuid = registerVerifyAndLoginReturningUuid("Hr Letter Offer Target");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("userUuid", targetUuid))
            .when()
                .post("/api/v1/hr/letters/OFFER")
            .then()
                .statusCode(200)
                .body("data.downloadUrl", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void issueInternshipLetter_noEligibilityCheck_returns200() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Letter Intern Manager", "HR_MANAGER");
        String targetUuid = registerVerifyAndLoginReturningUuid("Hr Letter Intern Target");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("userUuid", targetUuid))
            .when()
                .post("/api/v1/hr/letters/INTERNSHIP")
            .then()
                .statusCode(200)
                .body("data.downloadUrl", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void issueExperienceLetter_graduatedStudent_returns200() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Letter Grad Manager", "HR_MANAGER");
        String pmToken = registerVerifyGrantRoleAndLogin("Hr Letter Grad Pm", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String targetToken = registerVerifyAndLogin("Hr Letter Grad Student");
        long targetId = currentUserId(targetToken);
        long batchId = insertBatch(pmId);
        jdbcTemplate.update("INSERT INTO batch_students (batch_id, user_id, status) VALUES (?, ?, 'GRADUATED')",
                batchId, targetId);
        String targetUuid = uuidOf(targetId);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("userUuid", targetUuid))
            .when()
                .post("/api/v1/hr/letters/EXPERIENCE")
            .then()
                .statusCode(200)
                .body("data.downloadUrl", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void issueExperienceLetter_terminatedStudent_returns409() {
        String hrToken = registerVerifyGrantRoleAndLogin("Hr Letter Term Manager", "HR_MANAGER");
        String pmToken = registerVerifyGrantRoleAndLogin("Hr Letter Term Pm", "TRAINER_PM");
        long pmId = currentUserId(pmToken);
        String targetToken = registerVerifyAndLogin("Hr Letter Term Student");
        long targetId = currentUserId(targetToken);
        long batchId = insertBatch(pmId);
        jdbcTemplate.update("INSERT INTO batch_students (batch_id, user_id, status) VALUES (?, ?, 'TERMINATED')",
                batchId, targetId);
        String targetUuid = uuidOf(targetId);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of("userUuid", targetUuid))
            .when()
                .post("/api/v1/hr/letters/EXPERIENCE")
            .then()
                .statusCode(409)
                .body("error.code", equalTo("LETTER_NOT_ELIGIBLE"));
    }

    @Test
    void issueLetter_asStudent_returns403() {
        String studentToken = registerVerifyAndLogin("Hr Letter Forbid Student");
        String targetUuid = registerVerifyAndLoginReturningUuid("Hr Letter Forbid Target");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + studentToken)
                .body(Map.of("userUuid", targetUuid))
            .when()
                .post("/api/v1/hr/letters/OFFER")
            .then()
                .statusCode(403);
    }

    // --- helpers ---

    private byte[] minimalPdfBytes() {
        return "%PDF-1.4\n%%EOF".getBytes();
    }

    /** Creates an employee record for {@code employeeToken}'s user, called by {@code hrToken}. */
    private long createEmployeeFor(String hrToken, String employeeToken, String employeeCode) {
        long userId = currentUserId(employeeToken);
        String uuid = uuidOf(userId);
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + hrToken)
                .body(Map.of(
                        "userUuid", uuid, "employeeCode", employeeCode, "department", "Engineering",
                        "designation", "Trainer", "employmentType", "FULL_TIME",
                        "dateOfJoining", "2026-01-01", "baseSalary", 50000.00))
            .when()
                .post("/api/v1/hr/employees")
            .then()
                .statusCode(201)
                .extract().jsonPath().getLong("data.id");
    }

    private long insertBatch(long pmId) {
        String trackCode = "HRF-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO batches (name, track_code, pm_id, start_date, end_date, capacity, status)
                VALUES (?, ?, ?, CURRENT_DATE, CURRENT_DATE + INTERVAL 90 DAY, 20, 'ACTIVE')
                """, "Batch-" + UUID.randomUUID(), trackCode, pmId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM batches WHERE track_code = ? AND pm_id = ?", Long.class, trackCode, pmId);
    }

    private String uuidOf(long userId) {
        return jdbcTemplate.queryForObject("SELECT uuid FROM users WHERE id = ?", String.class, userId);
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

    private String registerVerifyAndLoginReturningUuid(String fullName) {
        String token = registerVerifyAndLogin(fullName);
        return uuidOf(currentUserId(token));
    }

    private String registerVerifyAndLogin(String fullName) {
        String email = uniqueEmail(fullName);
        given()
                .contentType(ContentType.JSON)
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
                .contentType(ContentType.JSON)
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
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "correct horse battery"))
            .when()
                .post("/api/v1/auth/login")
            .then()
                .statusCode(200)
                .body("data.twoFactorSetupRequired", equalTo(true))
                .extract().jsonPath().getString("data.challengeToken");

        String secretBase32 = given()
                .contentType(ContentType.JSON)
                .body(Map.of("challengeToken", challengeToken))
            .when()
                .post("/api/v1/auth/2fa/enable")
            .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.secret");
        byte[] secret = Base32Codec.decode(secretBase32);

        return given()
                .contentType(ContentType.JSON)
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
                .contentType(ContentType.JSON)
                .body(Map.of("token", raw))
            .when()
                .post("/api/v1/auth/verify-email")
            .then()
                .statusCode(200);
    }

    private String login(String email) {
        return given()
                .contentType(ContentType.JSON)
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
