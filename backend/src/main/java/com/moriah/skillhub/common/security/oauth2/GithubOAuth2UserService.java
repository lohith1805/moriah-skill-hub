package com.moriah.skillhub.common.security.oauth2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub's {@code /user} endpoint (what {@link DefaultOAuth2UserService} loads by default) omits
 * {@code email} entirely when the user has made their email private — which is GitHub's default
 * setting. There is also no {@code email_verified} attribute on that response at all, unlike
 * Google's OIDC ID token. This overrides the loaded attributes with a verified primary email
 * fetched from GitHub's separate {@code /user/emails} endpoint (needs the {@code user:email}
 * scope), so {@code OAuth2Service} (auth/) can read {@code "email"} the same way for both
 * providers without knowing GitHub needed a second call.
 * <p>
 * Lives in {@code common/security/oauth2}, not {@code auth/}, deliberately: this is pure
 * OAuth2-protocol plumbing (normalizing what a provider hands back), not account/business logic —
 * no DB access, no user creation. {@code SecurityConfig} (common/config) wires it directly, and
 * {@code common/} must never import a feature package (architecture.md invariant); keeping this
 * here avoids needing another exception to that rule the way {@code JwtAuthFilter} has one.
 */
@Service
@Slf4j
public class GithubOAuth2UserService extends DefaultOAuth2UserService {

    private static final String EMAILS_URI = "https://api.github.com/user/emails";
    private static final String USER_NAME_ATTRIBUTE = "id";

    // Audit 2026-08-31 (M15): explicit timeouts — RestClient.create() has none, so a hung
    // api.github.com response would block a login request thread indefinitely.
    private final RestClient restClient = RestClient.builder()
            .requestFactory(timeoutFactory())
            .build();

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(8));
        return factory;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User githubUser = super.loadUser(userRequest);

        String verifiedEmail = fetchVerifiedPrimaryEmail(userRequest.getAccessToken().getTokenValue());

        Map<String, Object> attributes = new HashMap<>(githubUser.getAttributes());
        attributes.put("email", verifiedEmail);

        return new DefaultOAuth2User(githubUser.getAuthorities(), attributes, USER_NAME_ATTRIBUTE);
    }

    private String fetchVerifiedPrimaryEmail(String accessToken) {
        List<GithubEmail> emails;
        try {
            emails = restClient.get()
                    .uri(EMAILS_URI)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GithubEmail>>() {
                    });
        } catch (Exception e) {
            log.warn("[oauth2/github] failed to fetch /user/emails: {}", e.getMessage());
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "github_email_fetch_failed", "Could not read a verified email from GitHub", null));
        }

        return emails.stream()
                .filter(GithubEmail::verified)
                .filter(GithubEmail::primary)
                .findFirst()
                .map(GithubEmail::email)
                .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(
                        "github_email_unverified", "GitHub account has no verified primary email", null)));
    }

    private record GithubEmail(String email, boolean primary, boolean verified) {
    }
}
