package com.moriah.skillhub;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Feature 23 hardening — proves {@code SecurityConfig}'s {@code .headers(...)} block actually
 * reaches a real HTTP response, not just that the DSL compiles. Hits {@code GET /api/v1/plans}
 * (one of {@code SecurityConfig.PUBLIC_PATHS} — no auth token needed, keeps this test independent
 * of the login flow) over the Testcontainers app's plain-HTTP {@code RANDOM_PORT}, exactly the
 * topology {@code SecurityConfig}'s own HSTS comment documents: no TLS at this layer, so HSTS is
 * only actually verifiable here because it was made unconditional
 * ({@code requestMatcher(AnyRequestMatcher.INSTANCE)}) — the previous, Spring-default-secure-only
 * behaviour would never emit the header in this exact test setup either, and would be silently
 * absent in the real deployment topology (TLS terminated upstream) for the same reason.
 */
class SecurityHeadersIT extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void publicEndpoint_returnsHardenedSecurityHeaders() {
        given()
        .when()
            .get("/api/v1/plans")
        .then()
            .statusCode(200)
            .header("X-Content-Type-Options", equalTo("nosniff"))
            .header("X-Frame-Options", equalTo("DENY"))
            .header("Strict-Transport-Security", equalTo("max-age=31536000 ; includeSubDomains"))
            .header("Content-Security-Policy", equalTo("default-src 'self'; frame-ancestors 'none'"));
    }
}
