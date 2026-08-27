package com.moriah.skillhub;

import com.moriah.skillhub.common.security.OpaqueTokenGenerator;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

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
import static org.hamcrest.Matchers.notNullValue;

/**
 * build-plan.md feature 09 verify line: "Completion recalculates on save. Public portfolio
 * contains no PII beyond name and title." Exercised as one continuous flow — register, verify,
 * login, then every {@code UserController} endpoint in the order a real client would call them.
 * Same register/verify helper pattern as {@code AuthFlowIT}: the raw verification token is never
 * exposed by the API, so it's inserted directly via JDBC with a hash computed the same way the
 * app does.
 */
class UserProfileFlowIT extends IntegrationTestBase {

    private static final byte[] PDF_BYTES = "%PDF-1.4 not a real pdf but starts right".getBytes(StandardCharsets.UTF_8);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void getMe_beforeAnyUpdate_lazilyCreatesAnEmptyProfileWithAPortfolioSlug() {
        String accessToken = registerVerifyAndLogin("Ada Lovelace");

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/api/v1/users/me")
        .then()
                .statusCode(200)
                .body("data.fullName", equalTo("Ada Lovelace"))
                .body("data.completionPercent", equalTo(0))
                .body("data.isComplete", equalTo(false))
                .body("data.hasResume", equalTo(false))
                .body("data.skills", equalTo(List.of()))
                .body("data.portfolioSlug", notNullValue());
    }

    @Test
    void getMe_withoutAToken_returns401() {
        given()
        .when()
                .get("/api/v1/users/me")
        .then()
                .statusCode(401);
    }

    @Test
    void updateProfile_thenGetMe_completionRecalculatesAndGithubUsernameFallbackIsSet() {
        String accessToken = registerVerifyAndLogin("Grace Hopper");

        Map<String, Object> body = Map.of(
                "githubUsername", "gracehopper",
                "bio", "Pioneer of compilers.",
                "location", "Arlington",
                "currentTitle", "Rear Admiral",
                "experienceLevel", "SENIOR",
                "yearsExperience", 40,
                "skills", List.of("COBOL", "Compilers"),
                "education", List.of(Map.of("institution", "Yale", "degree", "PhD", "fieldOfStudy", "Mathematics",
                        "startYear", 1930, "endYear", 1934)),
                "workExperience", List.of(Map.of("company", "US Navy", "title", "Rear Admiral",
                        "startDate", "1943-01-01", "description", "Naval computing pioneer")));

        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .body(body)
        .when()
                .put("/api/v1/users/me/profile")
        .then()
                .statusCode(200)
                .body("data.githubUsername", equalTo("gracehopper"))
                .body("data.skills", equalTo(List.of("COBOL", "Compilers")))
                // 9 of 10 completion fields filled (no resume yet) -> round(900/10) = 90
                .body("data.completionPercent", equalTo(90))
                .body("data.isComplete", equalTo(false));

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/api/v1/users/me")
        .then()
                .statusCode(200)
                .body("data.bio", equalTo("Pioneer of compilers."))
                .body("data.education[0].institution", equalTo("Yale"))
                .body("data.workExperience[0].company", equalTo("US Navy"));
    }

    @Test
    void uploadResume_thenDownloadUrl_worksAndCompletesTheProfileWithEveryOtherFieldFilled() {
        String accessToken = registerVerifyAndLogin("Margaret Hamilton");

        // fill every other completion field first, so the resume upload is what tips it to 100
        Map<String, Object> body = Map.of(
                "githubUsername", "mhamilton",
                "bio", "Led Apollo flight software.",
                "location", "Boston",
                "currentTitle", "Lead Engineer",
                "experienceLevel", "SENIOR",
                "yearsExperience", 15,
                "skills", List.of("Software Engineering"),
                "education", List.of(Map.of("institution", "Earlham College", "degree", "BA")),
                "workExperience", List.of(Map.of("company", "NASA", "title", "Director")));
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .body(body)
        .when()
                .put("/api/v1/users/me/profile")
        .then()
                .statusCode(200)
                .body("data.completionPercent", equalTo(90));

        given()
                .header("Authorization", "Bearer " + accessToken)
                .multiPart("file", "resume.pdf", PDF_BYTES, "application/pdf")
        .when()
                .post("/api/v1/users/me/resume")
        .then()
                .statusCode(200)
                .body("data.hasResume", equalTo(true))
                .body("data.completionPercent", equalTo(100))
                .body("data.isComplete", equalTo(true));

        Response downloadResponse = given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/api/v1/users/me/resume")
        .then()
                .statusCode(200)
                .body("data.downloadUrl", notNullValue())
                .extract().response();

        // Plain RestClient, not RestAssured, for this one call — same reasoning as
        // StorageServiceIT: RestAssured's own request builder re-encodes whatever URL it's given
        // (String or URI alike), double-encoding the presigned URL's already-percent-encoded AWS
        // SigV4 query string and corrupting the signature. .uri(URI), not .uri(String) — the
        // String overload has the identical problem on RestClient's side.
        String downloadUrl = downloadResponse.jsonPath().getString("data.downloadUrl");
        byte[] downloaded = RestClient.create().get().uri(URI.create(downloadUrl)).retrieve().body(byte[].class);
        assertThat(downloaded).isEqualTo(PDF_BYTES);
    }

    @Test
    void getResumeDownloadUrl_beforeAnyUpload_returns404ResumeNotFound() {
        String accessToken = registerVerifyAndLogin("No Resume Yet");

        given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/api/v1/users/me/resume")
        .then()
                .statusCode(404)
                .body("error.code", equalTo("RESUME_NOT_FOUND"));
    }

    @Test
    void uploadResume_notAPdf_returns400UnsupportedFileType() {
        String accessToken = registerVerifyAndLogin("Wrong File Type");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .multiPart("file", "resume.pdf", "this is not a pdf".getBytes(StandardCharsets.UTF_8), "application/pdf")
        .when()
                .post("/api/v1/users/me/resume")
        .then()
                .statusCode(400)
                .body("error.code", equalTo("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void getPortfolio_isPublicAndNeverLeaksEmailPhoneOrPrivateFields() {
        String accessToken = registerVerifyAndLogin("Katherine Johnson");

        Map<String, Object> body = Map.of(
                "bio", "Calculated orbital mechanics.",
                "currentTitle", "Mathematician",
                "location", "Hampton",
                "skills", List.of("Orbital Mechanics", "Fortran"));
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + accessToken)
                .body(body)
        .when()
                .put("/api/v1/users/me/profile")
        .then()
                .statusCode(200);

        String slug = given()
                .header("Authorization", "Bearer " + accessToken)
        .when()
                .get("/api/v1/users/me")
        .then()
                .statusCode(200)
                .extract().jsonPath().getString("data.portfolioSlug");

        // no Authorization header at all — this must work unauthenticated
        given()
        .when()
                .get("/api/v1/portfolio/" + slug)
        .then()
                .statusCode(200)
                .body("data.fullName", equalTo("Katherine Johnson"))
                .body("data.currentTitle", equalTo("Mathematician"))
                .body("data.skills", equalTo(List.of("Orbital Mechanics", "Fortran")))
                .body("data.email", equalTo(null))
                .body("data.phone", equalTo(null))
                .body("data.hasResume", equalTo(null))
                .body("data.completionPercent", equalTo(null));
    }

    @Test
    void getPortfolio_unknownSlug_returns404PortfolioNotFound() {
        given()
        .when()
                .get("/api/v1/portfolio/does-not-exist-00000000")
        .then()
                .statusCode(404)
                .body("error.code", equalTo("PORTFOLIO_NOT_FOUND"));
    }

    /** Same reasoning as {@code AuthFlowIT.registerUser}/{@code verifyEmailDirectly}: the raw
     * email-verification token is never exposed by the API or logged, so it's inserted directly
     * via JDBC with a hash computed the same way the app does. */
    private String registerVerifyAndLogin(String fullName) {
        String email = fullName.toLowerCase().replace(" ", ".") + "-" + UUID.randomUUID() + "@example.com";

        given()
                .contentType("application/json")
                .body(Map.of("fullName", fullName, "email", email, "password", "correct horse battery"))
        .when()
                .post("/api/v1/auth/register")
        .then()
                .statusCode(201);

        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        String rawToken = OpaqueTokenGenerator.generate();
        jdbcTemplate.update("""
                INSERT INTO email_verification_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, userId, OpaqueTokenGenerator.sha256Hex(rawToken), Instant.now().plus(1, ChronoUnit.HOURS));

        given()
                .contentType("application/json")
                .body(Map.of("token", rawToken))
        .when()
                .post("/api/v1/auth/verify-email")
        .then()
                .statusCode(200);

        Response loginResponse = given()
                .contentType("application/json")
                .body(Map.of("email", email, "password", "correct horse battery"))
        .when()
                .post("/api/v1/auth/login")
        .then()
                .statusCode(200)
                .extract().response();

        return loginResponse.jsonPath().getString("data.tokens.accessToken");
    }
}
