package com.moriah.skillhub.submission.dto;

import com.moriah.skillhub.submission.entity.ReviewVerdict;

import java.time.Instant;
import java.util.List;

public record ReviewResponse(
        Long id,
        Long submissionId,
        String reviewerUuid,
        String reviewerName,
        Integer score,
        ReviewVerdict verdict,
        String comments,
        List<InlineComment> inlineComments,
        Instant reviewedAt
) {
}
