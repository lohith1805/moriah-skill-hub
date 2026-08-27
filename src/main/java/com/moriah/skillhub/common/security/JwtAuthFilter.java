package com.moriah.skillhub.common.security;

import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserStatus;
import com.moriah.skillhub.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates a request if it carries a valid access token — never rejects one itself. An
 * invalid, expired, or absent token simply leaves the {@code SecurityContext} empty, and the
 * filter chain's normal {@code anyRequest().authenticated()} + {@code AuthenticationEntryPoint}
 * (SecurityConfig) handle the actual 401, already returning the project's {@code ApiResponse}
 * envelope — no need to duplicate that here.
 * <p>
 * A Spring bean (not manually constructed) so {@code SecurityConfig} can take it as a plain
 * constructor-injected field, per library-docs.md's Spring Security 6 pattern.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TokenRevocationService tokenRevocationService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        extractToken(request).flatMap(this::authenticate).ifPresent(
                auth -> SecurityContextHolder.getContext().setAuthentication(auth));

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }
        return Optional.empty();
    }

    private Optional<Authentication> authenticate(String token) {
        try {
            Jws<Claims> jws = jwtService.parse(token);
            Claims claims = jws.getPayload();

            String jti = claims.getId();
            if (tokenRevocationService.isDenylisted(jti)) {
                log.warn("[security/jwt] rejected denylisted jti");
                return Optional.empty();
            }

            String uuid = claims.getSubject();
            User user = userRepository.findByUuid(uuid).orElse(null);
            if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                log.warn("[security/jwt] token subject not found or not active");
                return Optional.empty();
            }

            Integer tvClaim = claims.get("tv", Integer.class);
            if (tvClaim == null || !tvClaim.equals(user.getTokenVersion())) {
                log.info("[security/jwt] rejected stale token_version for user {}", user.getUuid());
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(user.getId(), user.getUuid(), roles);
            List<GrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .map(GrantedAuthority.class::cast)
                    .toList();

            return Optional.of(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        } catch (JwtException | IllegalArgumentException e) {
            log.info("[security/jwt] token rejected: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
