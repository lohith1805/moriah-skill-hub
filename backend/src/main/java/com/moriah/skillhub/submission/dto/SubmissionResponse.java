package com.moriah.skillhub.submission.dto;

import com.moriah.skillhub.submission.entity.PrState;
import com.moriah.skillhub.submission.entity.SubmissionStatus;

import java.time.Instant;

/** {@code studentUuid}/{@code studentName}, never the internal {@code users.id} — same
 * convention as {@code TaskResponse.assignedToUuid}/{@code assignedToName}. {@code verifiedAt}
 * null means GitHub hasn't confirmed this one yet — either still pending the first call, or
 * waiting on {@code SubmissionVerificationRetryJob} after a GitHub outage. */
public record SubmissionResponse(
        Long id,
        Long taskId,
        String studentUuid,
        String studentName,
        int attemptNumber,
        String prUrl,
        String repoOwner,
        String repoName,
        Integer prNumber,
        PrState prState,
        Integer commitCount,
        String latestCommitSha,
        String videoUrl,
        String notes,
        SubmissionStatus status,
        Instant submittedAt,
        Instant verifiedAt
) {
}
