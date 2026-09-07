package com.moriah.skillhub.common.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Reached when the provider rejects the login (consent denied, invalid {@code state}/CSRF check,
 * provider outage) or a custom {@code OAuth2UserService} ({@link GithubOAuth2UserService}) throws.
 * Redirects the browser back to the SPA callback with {@code #error=oauth_failed} — same
 * redirect-not-JSON reasoning as {@link OAuth2AuthenticationSuccessHandler}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final OAuth2CookieProperties properties;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.warn("[oauth2/failure] {}", exception.getMessage());
        response.sendRedirect(properties.frontendRedirectUri() + "#error=oauth_failed");
    }
}
