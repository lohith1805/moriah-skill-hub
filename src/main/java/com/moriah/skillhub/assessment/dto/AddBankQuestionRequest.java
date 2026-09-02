package com.moriah.skillhub.assessment.dto;

import com.moriah.skillhub.assessment.entity.QuestionDifficulty;
import com.moriah.skillhub.assessment.entity.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code POST /api/v1/assessments/banks/{id}/questions} (gap B1.15). Same shape and cross-field
 * validation as {@code CreateQuestionRequest} (feature 14) plus an optional {@code difficulty}
 * tag — {@code options}/{@code correctAnswerIndices} are required and shape-checked for
 * {@code MCQ}/{@code MULTI_SELECT} and must be absent for {@code CODE}.
 */
public record AddBankQuestionRequest(
        @NotBlank @Size(max = 5000) String questionText,
        @NotNull QuestionType questionType,
        List<@NotBlank @Size(max = 500) String> options,
        List<@Min(0) Integer> correctAnswerIndices,
        @NotNull @Min(1) @Max(100) Integer marks,
        @Size(max = 2000) String explanation,
        QuestionDifficulty difficulty
) {
    public AddBankQuestionRequest {
        if (questionType == QuestionType.CODE) {
            if (options != null && !options.isEmpty()) {
                throw new IllegalArgumentException("A CODE question cannot have options.");
            }
            if (correctAnswerIndices != null && !correctAnswerIndices.isEmpty()) {
                throw new IllegalArgumentException("A CODE question cannot have a correct-answer key.");
            }
        } else {
            if (options == null || options.size() < 2) {
                throw new IllegalArgumentException("An " + questionType + " question needs at least 2 options.");
            }
            if (correctAnswerIndices == null || correctAnswerIndices.isEmpty()) {
                throw new IllegalArgumentException("An " + questionType + " question needs at least one correct answer.");
            }
            Set<Integer> distinct = new HashSet<>(correctAnswerIndices);
            if (distinct.size() != correctAnswerIndices.size()) {
                throw new IllegalArgumentException("correctAnswerIndices cannot contain duplicates.");
            }
            if (distinct.stream().anyMatch(i -> i >= options.size())) {
                throw new IllegalArgumentException("correctAnswerIndices must reference a valid option index.");
            }
            if (questionType == QuestionType.MCQ && correctAnswerIndices.size() != 1) {
                throw new IllegalArgumentException("An MCQ question needs exactly one correct answer.");
            }
        }
    }
}
