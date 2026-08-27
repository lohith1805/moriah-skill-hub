package com.moriah.skillhub.assessment;

import com.moriah.skillhub.assessment.entity.QuizAnswer;

import java.math.BigDecimal;
import java.util.List;

/** {@code GradingService.grade}'s output — internal to the service layer, not a client-facing
 * DTO (contrast with {@code assessment/dto/**}). {@code answers} are unsaved {@code QuizAnswer}
 * entities the caller persists; {@code percentage} is {@code null} whenever {@code
 * autoGradableMarks} is zero (a CODE-only quiz — see {@code QuizAttempt}'s Javadoc). No {@code
 * passed} field here — that needs the quiz's own {@code passPercentage}, which {@link
 * GradingService#grade} deliberately doesn't take (see its Javadoc); {@code
 * GradingService#applyResult} computes it separately. {@code hasCodeQuestions} drives the {@code
 * PENDING_MANUAL_GRADING} vs {@code SUBMITTED} decision — a property of the quiz's question set,
 * not of what the student happened to answer. */
record GradingResult(
        List<QuizAnswer> answers,
        BigDecimal autoGradedMarks,
        BigDecimal autoGradableMarks,
        BigDecimal percentage,
        boolean hasCodeQuestions
) {
}
