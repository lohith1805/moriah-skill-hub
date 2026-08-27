package com.moriah.skillhub.submission.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** An injected {@code RestClient} bean, not constructed inline per-call — {@link
 * GithubVerificationService} needs a mockable collaborator for its own unit test, same reasoning
 * {@code NotificationClientConfig}'s clients are beans rather than {@code RazorpayService}'s
 * inline {@code new RazorpayClient(...)}. library-docs.md "GitHub REST API": always send {@code
 * Accept: application/vnd.github+json} and {@code X-GitHub-Api-Version}.
 * <p>
 * Explicit connect/read timeouts, unlike every other {@code RestClient} bean in this codebase —
 * this is the first one called from a code path that (deliberately, see {@code
 * SubmissionService.create}'s Javadoc) can run without an enclosing transaction holding the
 * caller's DB connection, but a hanging GitHub response would still hang the request thread
 * itself and, for {@code SubmissionVerificationRetryJob}, the whole nightly-adjacent sweep. A
 * bound is cheap insurance either way; not retrofitted onto the other, unrelated gateways here. */
@Configuration
public class GithubClientConfig {

    private static final String API_VERSION = "2022-11-28";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;

    @Bean
    public RestClient githubRestClient(GithubProperties props) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        return RestClient.builder()
                .baseUrl(props.apiBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + props.apiToken())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                .build();
    }
}
