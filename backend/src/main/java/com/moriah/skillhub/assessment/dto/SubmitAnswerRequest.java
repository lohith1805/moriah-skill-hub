package com.moriah.skillhub.assessment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Exactly one of {@code selectedOptionIndices} (for {@code MCQ}/{@code MULTI_SELECT}) or {@code
 * codeAnswer} (for {@code CODE}) is meaningful for a given question — {@code GradingService}
 * reads whichever matches the question's actual type and ignores the other. An answer for a
 * question that turns out not to belong to this quiz, or that supplies neither field, is simply
 * treated as unanswered rather than rejecting the whole submission — one bad entry in the list
 * doesn't invalidate every other answer the student did provide. */
public record SubmitAnswerRequest(
        @NotNull Long questionId,
        List<Integer> selectedOptionIndices,
        @Size(max = 20000) String codeAnswer
) {
}
