package com.moriah.skillhub.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.ErrorDetail;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.security.JwtAuthFilter;
import com.moriah.skillhub.common.security.RateLimitFilter;
import com.moriah.skillhub.common.security.oauth2.GithubOAuth2UserService;
import com.moriah.skillhub.common.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.moriah.skillhub.common.security.oauth2.OAuth2AuthenticationSuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Filter chain, CORS, and method security. {@code RateLimitFilter} runs first (reject fast,
 * before any other work — build-plan.md feature 07's webhook-CPU-cost rationale applies to
 * every {@code permitAll()} path, not just webhooks), then {@code JwtAuthFilter}. Both are
 * explicitly ordered relative to each other, not just "before UsernamePasswordAuthenticationFilter"
 * independently — Spring Security does not guarantee a relative order between two custom filters
 * registered at the same nominal position otherwise.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * {@code /api/v1/auth/**} is public at the filter level per architecture.md's "Public
     * endpoints" list, including logout/logout-all — those identify what to revoke from the
     * refresh token in the request body, not from an Authorization header, so they need no
     * separately authenticated access token to function correctly or safely.
     * <p>
     * Feature 07 additions, same list: {@code GET /api/v1/plans} (public plan catalogue —
     * {@code PlanController} only ever exposes {@code GET} on this path, so making the whole
     * path public is harmless) and {@code /api/v1/webhooks/**} — gateways cannot present a JWT
     * (library-docs.md), secured entirely by HMAC signature verification inside the controller
     * instead. {@code /api/v1/subscriptions/**} (checkout, {@code /me}) deliberately stays out of
     * this list — those need a real caller identity, enforced via {@code @PreAuthorize} on each
     * method plus the default {@code anyRequest().authenticated()} below.
     * <p>
     * Feature 09 addition: {@code /api/v1/portfolio/**} (build-plan.md: "GET /portfolio/{slug}
     * public") — same "only ever exposes GET here" reasoning as {@code /api/v1/plans}, since
     * {@code UserController} never maps anything else under this prefix. {@code /api/v1/users/**}
     * deliberately stays out of this list, same reasoning as {@code /api/v1/subscriptions/**}.
     */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/auth/**",
            "/api/v1/plans",
            "/api/v1/webhooks/**",
            "/api/v1/portfolio/**"
    };

    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;
    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final GithubOAuth2UserService githubOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                // Order matters here: JwtAuthFilter must be registered first (relative to a
                // standard Spring Security filter) so it gets an order position in
                // FilterOrderRegistration — only then can rateLimitFilter be registered
                // relative to JwtAuthFilter.class. Reversing these two lines throws
                // "The Filter class ... does not have a registered order" at context startup.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthFilter.class)
                // build-plan.md feature 05's custom paths + `/architect feature 05`'s stateless
                // design: a cookie-based authorization-request repository (SecurityConfig is
                // SessionCreationPolicy.STATELESS, so Spring's session-based default can't work
                // here — see HttpCookieOAuth2AuthorizationRequestRepository), and success/failure
                // handlers that write the same ApiResponse JSON envelope as every other endpoint
                // instead of Spring's default browser redirect.
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(a -> a
                                .baseUri("/api/v1/auth/oauth2/authorize")
                                .authorizationRequestRepository(authorizationRequestRepository))
                        .redirectionEndpoint(r -> r.baseUri("/api/v1/auth/oauth2/callback/*"))
                        // Google's registration is OIDC — Spring's default OidcUserService already
                        // surfaces email/email_verified/name from the ID token correctly, no
                        // override needed. GitHub is plain OAuth2, where the equivalent hook is
                        // .userService(...), not .oidcUserService(...); this is the only
                        // non-OIDC registration in this project.
                        .userInfoEndpoint(u -> u.userService(githubOAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler))
                .build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> writeError(response, ErrorCode.UNAUTHENTICATED);
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeError(response, ErrorCode.INSUFFICIENT_ROLE);
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.failure(ErrorDetail.of(errorCode));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
