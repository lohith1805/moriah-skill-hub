package com.moriah.skillhub.assessment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.assessment.dto.SubmitAnswerRequest;
import com.moriah.skillhub.assessment.entity.QuestionType;
import com.moriah.skillhub.assessment.entity.Quiz;
import com.moriah.skillhub.assessment.entity.QuizAnswer;
import com.moriah.skillhub.assessment.entity.QuizAttempt;
import com.moriah.skillhub.assessment.entity.QuizQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * build-plan.md feature 14: auto-grades {@code MCQ}/{@code MULTI_SELECT}, leaves {@code CODE}
 * ungraded and out of the percentage denominator. Used by both {@code QuizService#submit} (a
 * student's own submission) and {@code QuizAttemptExpiryJob} (an abandoned attempt scored on
 * whatever was given, possibly nothing) — the grading arithmetic is identical either way, only
 * where the answers come from differs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class GradingService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Integer>> INT_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /** {@code questions} is the quiz's full, already-loaded question set (one flat query by the
     * caller — never re-queried here); {@code answersByQuestionId} is whatever the student
     * actually supplied, keyed by question id, possibly missing entries entirely for unanswered
     * questions (code-standards.md "N+1 Prevention": no repository call in this loop). */
    GradingResult grade(List<QuizQuestion> questions, Map<Long, SubmitAnswerRequest> answersByQuestionId) {
        List<QuizAnswer> answers = new ArrayList<>();
        // Scale 2 throughout, matching quiz_attempts.auto_graded_marks/auto_gradable_marks'
        // DECIMAL(6,2) columns and percentage's own scale-2 rounding below — BigDecimal.ZERO and
        // BigDecimal.valueOf(int) are both scale 0, and .add() preserves the larger operand's
        // scale, so without this the two marks fields would render as "10" while percentage
        // renders as "100.00", an inconsistent API response for no reason.
        BigDecimal autoGradedMarks = BigDecimal.ZERO.setScale(2);
        BigDecimal autoGradableMarks = BigDecimal.ZERO.setScale(2);
        boolean hasCodeQuestions = false;

        for (QuizQuestion question : questions) {
            SubmitAnswerRequest given = answersByQuestionId.get(question.getId());
            QuizAnswer answer = new QuizAnswer();
            answer.setQuestion(question);

            if (question.getQuestionType() == QuestionType.CODE) {
                hasCodeQuestions = true;
                String codeAnswer = given != null ? given.codeAnswer() : null;
                answer.setGivenAnswer(codeAnswer != null ? toJson(codeAnswer) : null);
                answer.setIsCorrect(null);
                answer.setMarksAwarded(null);
            } else {
                List<Integer> selected = (given != null && given.selectedOptionIndices() != null)
                        ? given.selectedOptionIndices() : List.of();
                Set<Integer> givenSet = new HashSet<>(selected);
                Set<Integer> correctSet = new HashSet<>(parseIndices(question.getCorrectAnswer()));
                boolean correct = !givenSet.isEmpty() && givenSet.equals(correctSet);
                BigDecimal marksAwarded = (correct ? BigDecimal.valueOf(question.getMarks()) : BigDecimal.ZERO).setScale(2);

                answer.setGivenAnswer(selected.isEmpty() ? null : toJson(selected));
                answer.setIsCorrect(correct);
                answer.setMarksAwarded(marksAwarded);

                autoGradableMarks = autoGradableMarks.add(BigDecimal.valueOf(question.getMarks()));
                autoGradedMarks = autoGradedMarks.add(marksAwarded);
            }
            answers.add(answer);
        }

        BigDecimal percentage = null;
        if (autoGradableMarks.compareTo(BigDecimal.ZERO) > 0) {
            percentage = autoGradedMarks.multiply(BigDecimal.valueOf(100))
                    .divide(autoGradableMarks, 2, RoundingMode.HALF_UP);
        }

        return new GradingResult(answers, autoGradedMarks, autoGradableMarks, percentage, hasCodeQuestions);
    }

    /** {@code passed} is computed against the quiz's own {@code passPercentage} here, not inside
     * {@link #grade} — {@code grade} doesn't need the {@code Quiz} itself, only its question set,
     * keeping it usable without loading the parent for every call. */
    boolean isPassing(BigDecimal percentage, Quiz quiz) {
        return percentage != null && percentage.compareTo(BigDecimal.valueOf(quiz.getPassPercentage())) >= 0;
    }

    void applyResult(QuizAttempt attempt, GradingResult result, Quiz quiz) {
        attempt.setAutoGradedMarks(result.autoGradedMarks());
        attempt.setAutoGradableMarks(result.autoGradableMarks());
        attempt.setPercentage(result.percentage());
        attempt.setPassed(result.percentage() == null ? null : isPassing(result.percentage(), quiz));
    }

    List<String> parseOptions(String json) {
        return parse(json, STRING_LIST);
    }

    List<Integer> parseIndices(String json) {
        return parse(json, INT_LIST);
    }

    /** {@code QuizAnswer.givenAnswer} for a {@code CODE} question is a JSON-encoded string (see
     * {@link #toJson}), not a JSON array — a separate accessor rather than overloading {@link
     * #parseOptions}/{@link #parseIndices}'s list-shaped return. */
    String parseCodeAnswer(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, String.class);
        } catch (JsonProcessingException e) {
            log.warn("[assessment/grading] failed to parse stored code answer, treating as null", e);
            return null;
        }
    }

    private <T> List<T> parse(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("[assessment/grading] failed to parse stored JSON, treating as empty", e);
            return List.of();
        }
    }

    /** Package-private, not {@code private} — {@code QuizService} reuses this for {@code
     * QuizQuestion.options}/{@code correctAnswer} serialization at creation time too, rather than
     * duplicating an identical helper (both classes live in this same {@code assessment}
     * package). */
    String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("[assessment/grading] failed to serialize answer value, storing null", e);
            return null;
        }
    }
}
