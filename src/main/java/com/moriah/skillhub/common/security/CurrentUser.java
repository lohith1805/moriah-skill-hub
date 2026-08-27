package com.moriah.skillhub.common.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Resolves the caller's {@code userId} from the {@code SecurityContext} — never accept a user id
 * from a request body or path variable for the caller's own identity (library-docs.md "Current
 * User Resolution"; that would be an authorization bypass).
 * <p>
 * The {@code instanceof} guard matters on any endpoint under {@code /api/v1/auth/**} (public at
 * the filter level) that still wants to know "is someone logged in" — e.g. feature 05's
 * {@code /2fa/enable}. On an unauthenticated request, Spring Security's default {@code
 * AnonymousAuthenticationFilter} still populates the {@code SecurityContext} with an {@code
 * AnonymousAuthenticationToken} whose principal is the bare string {@code "anonymousUser"}, not
 * {@code null} — evaluating a bare {@code userId} SpEL expression against that {@code String}
 * throws {@code SpelEvaluationException} ("no such property"), not a clean {@code null}. Checking
 * the principal's type first, and falling back to {@code null} otherwise, is what actually makes
 * an unauthenticated call resolve to {@code null} instead of an unhandled 500.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression =
        "#this instanceof T(com.moriah.skillhub.common.security.AuthenticatedPrincipal) ? userId : null")
public @interface CurrentUser {
}
