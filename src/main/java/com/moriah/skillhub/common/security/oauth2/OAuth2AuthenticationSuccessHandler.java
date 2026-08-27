package com.moriah.skillhub.common.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.auth.OAuth2Service;
import com.moriah.skillhub.auth.dto.LoginResponse;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.ErrorDetail;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Replaces Spring Security's default redirect-based OAuth2 login success behaviour — build-plan.md
 * feature 05: "Issues the same JWT pair as password login," and `/architect feature 05` settled
 * on a JSON body over a redirect (never puts an access token in a URL). This is a servlet-{@link
 * jakarta.servlet.Filter}-level component, not a {@code @RestController} — an exception thrown
 * here never reaches {@code GlobalExceptionHandler} (that only wraps Spring MVC dispatch, which
 * this request never reaches), so the same envelope is written by hand here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2Service oAuth2Service;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

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

            writeJson(response, HttpStatus.OK, ApiResponse.success(loginResponse));
        } catch (BusinessException e) {
            log.warn("[oauth2/success] {}", e.getMessage());
            writeJson(response, e.getErrorCode().status(),
                    ApiResponse.failure(ErrorDetail.of(e.getErrorCode(), e.getMessage())));
        } catch (ForbiddenOperationException e) {
            log.warn("[oauth2/success] {}", e.getMessage());
            writeJson(response, e.getErrorCode().status(), ApiResponse.failure(ErrorDetail.of(e.getErrorCode())));
        } catch (RuntimeException e) {
            log.error("[oauth2/success] unexpected error completing OAuth2 login", e);
            writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiResponse.failure(ErrorDetail.of(ErrorCode.INTERNAL_ERROR)));
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

    private void writeJson(HttpServletResponse response, HttpStatus status, ApiResponse<?> body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
