package com.moriah.skillhub.common.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Spring Security's default {@code AuthorizationRequestRepository} stores the in-flight {@code
 * OAuth2AuthorizationRequest} in the {@code HttpSession} between the authorize redirect and the
 * provider's callback — but {@code SecurityConfig} is fully stateless ({@code
 * SessionCreationPolicy.STATELESS}, `/architect feature 05`). This stores it in a short-lived,
 * HttpOnly cookie instead: the request is serialized (it implements {@link java.io.Serializable}
 * for exactly this reason), Base64-encoded, and round-tripped through the browser rather than
 * server-side session state — the standard approach for OAuth2 login in a stateless API. The
 * cookie's max-age is externalized to {@link OAuth2CookieProperties} (`/review` follow-up), not
 * hardcoded here — a {@code @Component} like {@code RateLimitFilter}/{@code JwtAuthFilter}
 * rather than manually {@code new}'d in {@code SecurityConfig}, so it gets that dependency the
 * same way every other filter/handler in this feature does.
 */
@Component
@RequiredArgsConstructor
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";

    private final OAuth2CookieProperties oAuth2CookieProperties;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request)
                .map(HttpCookieOAuth2AuthorizationRequestRepository::deserialize)
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

        String serialized = serialize(authorizationRequest);
        addCookie(response, request.isSecure(), serialized, oAuth2CookieProperties.authorizationCookieMaxAgeSeconds());
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
        // our own callback endpoint, which Lax cookies are sent on — no need for the broader
        // (and Secure-mandatory) None. `secure` mirrors the incoming request's own scheme so this
        // works over plain http in local dev and https in any real deployment without a
        // per-environment switch.
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        return Base64.getUrlEncoder()
                .encodeToString(SerializationUtils.serialize(authorizationRequest));
    }

    private static OAuth2AuthorizationRequest deserialize(String cookieValue) {
        Object value = SerializationUtils.deserialize(Base64.getUrlDecoder().decode(cookieValue));
        return (OAuth2AuthorizationRequest) value;
    }
}
