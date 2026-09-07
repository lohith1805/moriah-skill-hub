package com.moriah.skillhub.submission.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** A server-side personal access token for GitHub's REST API — separate from {@code
 * spring.security.oauth2.client.registration.github}'s client-id/secret, which is only ever used
 * for the interactive OAuth2 login flow (a different GitHub app, a different credential). Bare
 * env var, same treatment as every other gateway credential in this project (no secret store
 * exists yet) — same package placement as {@code payment/gateway/RazorpayProperties}. */
@ConfigurationProperties(prefix = "moriah.github")
public record GithubProperties(String apiToken, String apiBaseUrl) {
}
