package com.moriah.skillhub;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Feature 01 verify criteria: the application starts against a real MySQL instance and
 * {@code /actuator/health} — the one public endpoint at this stage — returns {@code UP} with
 * no authentication.
 */
class SkillHubApplicationIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void contextLoads() {
        // Flyway ran (no migrations exist yet at feature 01) and ddl-auto=validate passed —
        // an ApplicationContext failure here means one of the two disagrees with the schema.
    }

    @Test
    void actuatorHealthIsPublicAndUp() {
        given()
            .when().get("/actuator/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void unauthenticatedRequestToAnUnknownProtectedPathReturnsTheApiResponseEnvelope() {
        // No business endpoints exist yet at feature 01. This confirms the security filter
        // chain itself returns the project's ApiResponse envelope on a 401, not Spring
        // Security's default response, satisfying "every endpoint returns ApiResponse<T>"
        // at the filter-chain level, ahead of any controller existing to enforce it directly.
        given()
            .when().get("/api/v1/does-not-exist-yet")
            .then()
                .statusCode(401)
                .body("success", equalTo(false))
                .body("error.code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    void swaggerUiAndOpenApiSpecAreReachableWithoutAuthentication() {
        given()
            .when().get("/v3/api-docs")
            .then()
                .statusCode(200)
                .body("info.title", equalTo("Moriah Skill Hub API"));

        given()
            .when().get("/swagger-ui.html")
            .then()
                .statusCode(200);
    }
}
