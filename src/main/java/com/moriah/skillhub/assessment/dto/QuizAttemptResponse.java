package com.moriah.skillhub.assessment.dto;

import com.moriah.skillhub.assessment.entity.AttemptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QuizAttemptResponse(
        Long id,
        Long quizId,
        String quizTitle,
        Integer attemptNumber,
        AttemptStatus status,
        Instant startedAt,
        Instant submittedAt,
        Integer durationMinutes,
        List<AnswerResultView> questions,
        BigDecimal autoGradedMarks,
        BigDecimal autoGradableMarks,
        BigDecimal percentage,
        Boolean passed
) {
}
