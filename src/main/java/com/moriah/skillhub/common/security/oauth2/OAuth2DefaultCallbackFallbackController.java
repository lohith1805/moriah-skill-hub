package com.moriah.skillhub.common.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Safety net for the one OAuth2 path this app deliberately does <em>not</em> wire up.
 *
 * <p>Spring Security's out-of-the-box OAuth2 login redirection endpoint is
 * {@code /login/oauth2/code/{registrationId}}. {@code SecurityConfig} moves the real one to
 * {@code /api/v1/auth/oauth2/callback/*} and {@code application.yml} now pins each registration's
 * {@code redirect-uri} to match — so a correctly configured provider never lands here.
 *
 * <p>But a provider whose OAuth app still has the <em>old</em> default redirect URI registered
 * (a very common copy-paste from tutorials) will bounce the browser to
 * {@code /login/oauth2/code/google} with a real {@code code}/{@code state}. Nothing maps that
 * path, so it used to fall through to {@code anyRequest().authenticated()} and render a raw
 * {@code {"error":{"code":"UNAUTHENTICATED"}}} JSON body — a dead end for a human in a browser.
 *
 * <p>This controller catches that path (it is in {@code SecurityConfig.PUBLIC_PATHS}) and turns
 * it into the same {@code #error=} fragment redirect to the SPA that
 * {@link OAuth2AuthenticationFailureHandler} produces, so the user ends up back on the sign-in
 * screen with a message instead of staring at JSON. It never completes a login — a {@code code}
 * that arrives here cannot be trusted to have gone through the CSRF-checked authorization-request
 * repository — it only fails gracefully.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class OAuth2DefaultCallbackFallbackController {

    private final OAuth2CookieProperties properties;

    @GetMapping("/login/oauth2/code/**")
    public void handleMisroutedCallback(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(name = "error", required = false) String providerError) throws IOException {

        String code = (providerError != null && !providerError.isBlank())
                ? providerError
                : "oauth_callback_misrouted";
        log.warn("[oauth2/fallback] callback hit the default path {} (provider redirect URI is misconfigured; "
                + "expected /api/v1/auth/oauth2/callback/*) — bouncing to the SPA with error={}",
                request.getRequestURI(), code);

        String target = properties.frontendRedirectUri() + "#error="
                + URLEncoder.encode(code, StandardCharsets.UTF_8);
        response.sendRedirect(target);
    }
}
