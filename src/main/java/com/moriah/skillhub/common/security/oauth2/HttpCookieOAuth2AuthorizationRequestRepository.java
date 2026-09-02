package com.moriah.skillhub.common.security.oauth2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.security.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Spring Security's default {@code AuthorizationRequestRepository} stores the in-flight {@code
 * OAuth2AuthorizationRequest} in the {@code HttpSession} between the authorize redirect and the
 * provider's callback — but {@code SecurityConfig} is fully stateless ({@code
 * SessionCreationPolicy.STATELESS}, `/architect feature 05`). This stores it in a short-lived,
 * HttpOnly cookie instead.
 * <p>
 * <b>Audit 2026-08-31 (C1):</b> this used to round-trip the request through {@code
 * SerializationUtils.serialize}/{@code deserialize} — raw JDK serialization of an
 * attacker-controlled cookie value, {@code readObject()}'d on a {@code permitAll()} path before
 * any authentication runs (CWE-502: unauthenticated DoS guaranteed, RCE-class gadget surface,
 * plus login-CSRF because the cookie had no integrity protection). It now serialises a minimal,
 * non-polymorphic JSON DTO ({@link CookiePayload} — only the fields needed to rebuild the
 * request) and appends an HMAC-SHA256 tag keyed off the server's JWT secret with a fixed
 * domain-separation label. The read path verifies the tag in constant time and rejects anything
 * that does not match by returning {@code null} — which Spring surfaces as a clean
 * {@code OAuth2AuthenticationException} through {@code OAuth2AuthenticationFailureHandler}, never
 * a deserialization side effect. No {@code ObjectInputStream}, no gadget surface, no tampering.
 * <p>
 * The cookie's max-age is externalized to {@link OAuth2CookieProperties}, not hardcoded here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";

    /** Domain separation: prefixed to the signed bytes so this tag can never be confused with,
     * or replayed as, a token signed elsewhere with the same secret. */
    private static final String HMAC_CONTEXT = "moriah:oauth2-authz-request:v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DEC = Base64.getUrlDecoder();

    private final OAuth2CookieProperties oAuth2CookieProperties;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request)
                .flatMap(this::fromCookieValue)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            @Nullable OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(request, response);
            return;
        }

        String value = toCookieValue(authorizationRequest);
        addCookie(response, request.isSecure(), value, oAuth2CookieProperties.authorizationCookieMaxAgeSeconds());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        deleteCookie(request, response);
        return authorizationRequest;
    }

    private Optional<String> readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .findFirst();
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response) {
        addCookie(response, request.isSecure(), "", 0);
    }

    private void addCookie(HttpServletResponse response, boolean secure, String value, int maxAgeSeconds) {
        // Lax, not None: the browser's return from the provider is a top-level GET navigation to
        // our own callback endpoint, which Lax cookies are sent on. `secure` mirrors the incoming
        // request's own scheme so this works over plain http in local dev and https behind real
        // TLS termination without a per-environment switch.
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // --- signed-JSON codec -------------------------------------------------------------------

    private String toCookieValue(OAuth2AuthorizationRequest r) {
        CookiePayload payload = new CookiePayload(
                r.getAuthorizationUri(),
                r.getClientId(),
                r.getRedirectUri(),
                r.getScopes(),
                r.getState(),
                r.getAdditionalParameters(),
                r.getAttributes());
        try {
            String body = B64.encodeToString(objectMapper.writeValueAsBytes(payload));
            return body + "." + B64.encodeToString(hmac(body));
        } catch (Exception e) {
            // Serialising our own well-formed request should never fail; if it somehow does,
            // fail the login rather than write an unverifiable cookie.
            throw new IllegalStateException("Unable to serialise OAuth2 authorization request", e);
        }
    }

    private Optional<OAuth2AuthorizationRequest> fromCookieValue(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return Optional.empty();
        }
        int dot = cookieValue.lastIndexOf('.');
        if (dot <= 0 || dot == cookieValue.length() - 1) {
            log.debug("[oauth2] auth-request cookie has no signature segment; ignoring");
            return Optional.empty();
        }
        String body = cookieValue.substring(0, dot);
        byte[] presented;
        try {
            presented = B64_DEC.decode(cookieValue.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(hmac(body), presented)) {
            log.warn("[oauth2] auth-request cookie signature mismatch; ignoring tampered value");
            return Optional.empty();
        }
        try {
            CookiePayload p = objectMapper.readValue(B64_DEC.decode(body), CookiePayload.class);
            OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(p.authorizationUri())
                    .clientId(p.clientId())
                    .redirectUri(p.redirectUri())
                    .scopes(p.scopes())
                    .state(p.state());
            if (p.additionalParameters() != null) {
                builder.additionalParameters(p.additionalParameters());
            }
            if (p.attributes() != null) {
                builder.attributes(p.attributes());
            }
            return Optional.of(builder.build());
        } catch (Exception e) {
            log.warn("[oauth2] auth-request cookie payload could not be parsed; ignoring", e);
            return Optional.empty();
        }
    }

    private byte[] hmac(String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            mac.update(HMAC_CONTEXT.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CookiePayload(
            String authorizationUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            Map<String, Object> additionalParameters,
            Map<String, Object> attributes) {
    }
}
