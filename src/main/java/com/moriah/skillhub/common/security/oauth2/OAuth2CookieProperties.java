package com.moriah.skillhub.common.security.oauth2;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code authorizationCookieMaxAgeSeconds} — how long {@link HttpCookieOAuth2AuthorizationRequestRepository}'s
 * cookie survives. Externalized rather than a hardcoded constant, matching the `/review` precedent
 * set for every other timing value in this feature set — the whole authorize -> provider ->
 * callback round trip should take seconds, but a slow provider or a captive-portal-style redirect
 * chain is exactly the kind of thing worth tuning per environment without a code change.
 */
@ConfigurationProperties(prefix = "moriah.security.oauth2")
public record OAuth2CookieProperties(int authorizationCookieMaxAgeSeconds) {
}
