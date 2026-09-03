package com.moriah.skillhub.common.security.oauth2;

import com.moriah.skillhub.auth.OAuth2Service;
import com.moriah.skillhub.auth.dto.LoginResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Completes OAuth2 login, then <em>redirects the browser back to the SPA</em> with the login
 * result in the URL fragment ({@code #accessToken=...}). OAuth2 is a full browser redirect chain,
 * so the SPA never gets to read a JSON response body from this navigation — a redirect is the only
 * thing that reaches it. The fragment (not the query string) keeps the token off the wire to any
 * server, out of {@code Referer} headers, and out of access logs, so {@code /architect feature 05}'s
 * "no access token in a URL query" rule still holds. The target URL is
 * {@link OAuth2CookieProperties#frontendRedirectUri()}.
 *
 * <p>Servlet-{@link jakarta.servlet.Filter}-level component, not a {@code @RestController} — an
 * exception here never reaches {@code GlobalExceptionHandler}, so failures are turned into a
 * {@code #error=} redirect by hand.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2Service oAuth2Service;
    private final ClientIpResolver clientIpResolver;
    private final OAuth2CookieProperties properties;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        try {
            String registrationId = registrationId(authentication);
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            String userAgent = request.getHeader("User-Agent");
            String ipAddress = clientIpResolver.resolve(request);

            LoginResponse loginResponse = oAuth2Service.handleOAuth2Login(
                    registrationId, oAuth2User, userAgent, ipAddress);

            Map<String, String> params = new LinkedHashMap<>();
            if (loginResponse.twoFactorRequired()) {
                params.put("twoFactorRequired", "true");
                params.put("twoFactorSetupRequired", String.valueOf(loginResponse.twoFactorSetupRequired()));
                params.put("challengeToken", loginResponse.challengeToken());
            } else {
                params.put("accessToken", loginResponse.tokens().accessToken());
                params.put("refreshToken", loginResponse.tokens().refreshToken());
                params.put("expiresIn", String.valueOf(loginResponse.tokens().expiresInSeconds()));
            }
            redirectWithFragment(response, params);
        } catch (BusinessException e) {
            log.warn("[oauth2/success] {}", e.getMessage());
            redirectWithFragment(response, Map.of("error", e.getErrorCode().name()));
        } catch (ForbiddenOperationException e) {
            log.warn("[oauth2/success] {}", e.getMessage());
            redirectWithFragment(response, Map.of("error", e.getErrorCode().name()));
        } catch (RuntimeException e) {
            log.error("[oauth2/success] unexpected error completing OAuth2 login", e);
            redirectWithFragment(response, Map.of("error", "INTERNAL_ERROR"));
        }
    }

    private String registrationId(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            return oauthToken.getAuthorizedClientRegistrationId();
        }
        // Every principal reaching this handler comes through the OAuth2 login filter, which
        // always produces an OAuth2AuthenticationToken — this is defensive, not an expected path.
        throw new IllegalStateException(
                "Expected OAuth2AuthenticationToken, got " + authentication.getClass());
    }

    private void redirectWithFragment(HttpServletResponse response, Map<String, String> params) throws IOException {
        StringBuilder fragment = new StringBuilder();
        params.forEach((k, v) -> {
            if (v == null) return;
            if (fragment.length() > 0) fragment.append('&');
            fragment.append(urlEncode(k)).append('=').append(urlEncode(v));
        });
        response.sendRedirect(properties.frontendRedirectUri() + "#" + fragment);
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
