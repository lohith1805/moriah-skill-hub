package com.moriah.skillhub.submission.gateway;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * A separate bean from {@link GithubVerificationService} deliberately — {@code @Cacheable} is
 * proxy-based AOP, and a method calling another {@code @Cacheable} method on {@code this} within
 * the same class bypasses the proxy entirely, so the cache would silently never be hit. Same
 * fix, same reasoning as {@code EntitlementFlagsLoader} being split out from {@code
 * EntitlementGuard} (feature 04) and {@code NotificationRowWriter} from {@code
 * NotificationWriter} (feature 08) — cross-bean calls go through the proxy correctly.
 * <p>
 * {@code public}, not package-private — {@code SubmissionFlowIT} replaces this exact bean with a
 * Mockito mock (no real GitHub PAT exists in CI, and GitHub isn't a Testcontainers-able
 * dependency the way MySQL/Redis/MinIO are), which needs the type visible from the root test
 * package.
 */
@Component
@RequiredArgsConstructor
public class GithubPrFetcher {

    private final RestClient githubRestClient;

    /** Cached 5 minutes, keyed {@code owner/repo/prNumber} — library-docs.md: "a batch of 30
     * students refreshing a page must not burn 30 calls" against the 5,000/hr authenticated rate
     * limit. */
    @Cacheable(value = "githubPr", key = "#owner + '/' + #repo + '/' + #prNumber")
    public PullRequestDto fetch(String owner, String repo, int prNumber) {
        try {
            return githubRestClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, prNumber)
                    .retrieve()
                    .body(PullRequestDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(ErrorCode.PR_NOT_FOUND);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new GithubUnavailableException(e);
        }
    }

    public record PullRequestDto(GithubUser user, String state, boolean merged, Integer commits, Head head) {
        public record GithubUser(String login) {
        }

        public record Head(String sha) {
        }
    }
}
