package com.moriah.skillhub.common.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.ErrorDetail;
import com.moriah.skillhub.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Reached when the provider itself rejects the login (consent denied, invalid {@code state}/CSRF
 * check, provider outage) or when a custom {@code OAuth2UserService}
 * ({@link GithubOAuth2UserService}) throws — never a redirect, same reasoning as {@link
 * OAuth2AuthenticationSuccessHandler}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.warn("[oauth2/failure] {}", exception.getMessage());

        response.setStatus(ErrorCode.INVALID_CREDENTIALS.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.failure(ErrorDetail.of(ErrorCode.INVALID_CREDENTIALS));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
