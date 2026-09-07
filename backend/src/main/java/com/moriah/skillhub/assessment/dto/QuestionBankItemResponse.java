package com.moriah.skillhub.assessment.dto;

import com.moriah.skillhub.assessment.entity.QuestionDifficulty;
import com.moriah.skillhub.assessment.entity.QuestionType;

import java.time.Instant;
import java.util.List;

/**
 * One question in a bank (gap B1.15). {@code correctAnswer} is deliberately absent — same as
 * {@code QuizService}'s question responses, a bank question's key never leaves the service
 * layer. {@code options} is the list of choice strings ({@code null} for a {@code CODE} question).
 */
public record QuestionBankItemResponse(
        Long id,
        Long bankId,
        String questionText,
        QuestionType questionType,
        List<String> options,
        Integer marks,
        String explanation,
        QuestionDifficulty difficulty,
        String createdByUuid,
        Instant createdAt
) {
}
