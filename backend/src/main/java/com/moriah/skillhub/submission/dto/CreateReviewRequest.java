package com.moriah.skillhub.submission.dto;

import com.moriah.skillhub.submission.entity.ReviewVerdict;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReviewRequest(
        @NotNull Long submissionId,
        @Min(1) @Max(10) Integer score,
        @NotNull ReviewVerdict verdict,
        @Size(max = 5000) String comments,
        @Size(max = 50) List<@Valid InlineComment> inlineComments
) {
}
