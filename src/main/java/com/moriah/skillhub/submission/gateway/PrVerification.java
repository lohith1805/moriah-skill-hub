package com.moriah.skillhub.submission.gateway;

import com.moriah.skillhub.submission.entity.PrState;

/** The result of a successful {@link GithubVerificationService#verify} call — everything {@code
 * SubmissionService} needs to persist onto a {@code TaskSubmission} row. */
public record PrVerification(
        String owner,
        String repo,
        int prNumber,
        PrState state,
        Integer commitCount,
        String latestCommitSha
) {
}
