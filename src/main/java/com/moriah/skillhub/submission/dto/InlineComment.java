package com.moriah.skillhub.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** One entry in {@code CodeReview.inlineComments} — round-tripped through JSON text by {@code
 * ReviewService}, same treatment as {@code UserProfile}'s {@code skills}/{@code education}
 * fields (feature 09): a typed record, not raw/untyped JSON, so the request body actually gets
 * validated. */
public record InlineComment(
        @NotBlank @Size(max = 500) String filePath,
        @Positive Integer line,
        @NotBlank @Size(max = 2000) String comment
) {
}
