package com.moriah.skillhub.assessment.dto;

import com.moriah.skillhub.assessment.entity.QuestionType;

import java.math.BigDecimal;
import java.util.List;

/** Never carries the correct-answer key, at any attempt status — build-plan.md feature 14:
 * "Correct answers never sent to the client." {@code isCorrect}/{@code marksAwarded}/{@code
 * givenAnswer}/{@code explanation} stay {@code null} while the attempt is {@code IN_PROGRESS}
 * (nothing to reveal yet); {@code explanation} is populated only once the attempt has a terminal
 * status, matching common post-submission review UX without ever exposing the answer key itself. */
public record AnswerResultView(
        Long questionId,
        String questionText,
        QuestionType questionType,
        List<String> options,
        Integer marks,
        List<Integer> givenAnswerIndices,
        String givenCodeAnswer,
        Boolean isCorrect,
        BigDecimal marksAwarded,
        String explanation
) {
}
