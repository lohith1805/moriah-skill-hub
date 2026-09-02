package com.moriah.skillhub.submission.gateway;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.submission.entity.PrState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * library-docs.md "GitHub REST API", applied directly. Never clones, never downloads a diff —
 * existence and metadata only. The actual HTTP call + Redis caching lives in {@link
 * GithubPrFetcher}, a separate bean (see its Javadoc for why).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GithubVerificationService {

    // Audit 2026-08-31 (L3): owner/repo segments must start and end alphanumeric, so a segment
    // can never be "." or ".." (which would expand to /repos/../../pulls/N against api.github.com).
    private static final String SEGMENT = "[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?";
    private static final Pattern PR_URL =
            Pattern.compile("^https://github\\.com/(" + SEGMENT + ")/(" + SEGMENT + ")/pull/(\\d+)/?$");

    private final GithubPrFetcher githubPrFetcher;

    /**
     * @return empty when GitHub itself is unreachable right now (build-plan.md feature 12: "An
     *         outage never blocks a student") — the caller persists the submission with {@code
     *         verified_at = null} and lets {@code SubmissionVerificationRetryJob} try again.
     * @throws BusinessException {@code INVALID_PR_URL} for a malformed URL, {@code PR_NOT_FOUND}
     *         if GitHub genuinely has no such PR
     * @throws ForbiddenOperationException {@code PR_AUTHOR_MISMATCH} if the PR's author isn't
     *         {@code expectedGithubUsername}
     */
    public Optional<PrVerification> verify(String prUrl, String expectedGithubUsername) {
        Matcher matcher = PR_URL.matcher(prUrl == null ? "" : prUrl.trim());
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_PR_URL);
        }
        String owner = matcher.group(1);
        String repo = matcher.group(2);
        int prNumber = Integer.parseInt(matcher.group(3));

        GithubPrFetcher.PullRequestDto pr;
        try {
            pr = githubPrFetcher.fetch(owner, repo, prNumber);
        } catch (GithubUnavailableException e) {
            log.warn("[submission/github] GitHub unreachable verifying {}/{}#{}, will retry later: {}",
                    owner, repo, prNumber, e.getCause().getMessage());
            return Optional.empty();
        }

        if (expectedGithubUsername == null || !expectedGithubUsername.equalsIgnoreCase(pr.user().login())) {
            throw new ForbiddenOperationException(ErrorCode.PR_AUTHOR_MISMATCH);
        }

        PrState state = pr.merged() ? PrState.MERGED : "open".equalsIgnoreCase(pr.state()) ? PrState.OPEN : PrState.CLOSED;
        return Optional.of(new PrVerification(owner, repo, prNumber, state, pr.commits(), pr.head().sha()));
    }
}
