package com.moriah.skillhub.common.security.oauth2;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code authorizationCookieMaxAgeSeconds} — how long {@link HttpCookieOAuth2AuthorizationRequestRepository}'s
 * cookie survives. Externalized rather than a hardcoded constant, matching the `/review` precedent
 * set for every other timing value in this feature set — the whole authorize -> provider ->
 * callback round trip should take seconds, but a slow provider or a captive-portal-style redirect
 * chain is exactly the kind of thing worth tuning per environment without a code change.
 *
 * <p>{@code frontendRedirectUri} — where the OAuth2 success/failure handlers send the browser once
 * the provider round trip finishes. The SPA (single-page app) needs a login result it can act on;
 * the handlers append it to this URI's <em>fragment</em> ({@code #accessToken=...}), which browsers
 * never transmit to a server and which is absent from {@code Referer} headers and access logs — so
 * the "no access token in a URL query string" rule from {@code /architect feature 05} still holds.
 * The earlier JSON-body approach could not work end to end: OAuth2 is a full browser redirect, so
 * the SPA never gets to read a JSON response body from the callback navigation.
 */
@ConfigurationProperties(prefix = "moriah.security.oauth2")
public record OAuth2CookieProperties(int authorizationCookieMaxAgeSeconds, String frontendRedirectUri) {
}
