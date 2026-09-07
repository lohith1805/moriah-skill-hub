package com.moriah.skillhub.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.assessment.dto.SubmitAnswerRequest;
import com.moriah.skillhub.assessment.entity.QuestionType;
import com.moriah.skillhub.assessment.entity.Quiz;
import com.moriah.skillhub.assessment.entity.QuizAttempt;
import com.moriah.skillhub.assessment.entity.QuizQuestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** build-plan.md feature 14: "Auto-grades MCQ and MULTI_SELECT ... CODE answers ... excluded
 * from the percentage denominator ... Scoring them zero would fail students on unmarked work."
 * No mocks needed — {@code GradingService} has no collaborators beyond a real {@code
 * ObjectMapper}. */
class GradingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GradingService gradingService = new GradingService(objectMapper);

    private QuizQuestion mcq(long id, int marks, int... correctIndices) {
        return question(id, QuestionType.MCQ, marks, List.of("A", "B", "C"), correctIndices);
    }

    private QuizQuestion multiSelect(long id, int marks, int... correctIndices) {
        return question(id, QuestionType.MULTI_SELECT, marks, List.of("A", "B", "C", "D"), correctIndices);
    }

    private QuizQuestion code(long id, int marks) {
        QuizQuestion q = new QuizQuestion();
        q.setId(id);
        q.setQuestionType(QuestionType.CODE);
        q.setMarks(marks);
        return q;
    }

    private QuizQuestion question(long id, QuestionType type, int marks, List<String> options, int... correctIndices) {
        QuizQuestion q = new QuizQuestion();
        q.setId(id);
        q.setQuestionType(type);
        q.setMarks(marks);
        q.setOptions(toJson(options));
        q.setCorrectAnswer(toJson(boxed(correctIndices)));
        return q;
    }

    private List<Integer> boxed(int... values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SubmitAnswerRequest choice(long questionId, Integer... indices) {
        return new SubmitAnswerRequest(questionId, List.of(indices), null);
    }

    @Test
    void grade_mcqExactMatch_isCorrectAndAwardsFullMarks() {
        QuizQuestion q = mcq(1L, 10, 1);
        GradingResult result = gradingService.grade(List.of(q), Map.of(1L, choice(1L, 1)));

        assertThat(result.answers().get(0).getIsCorrect()).isTrue();
        assertThat(result.answers().get(0).getMarksAwarded()).isEqualByComparingTo("10");
        assertThat(result.autoGradedMarks()).isEqualByComparingTo("10");
        assertThat(result.autoGradableMarks()).isEqualByComparingTo("10");
        assertThat(result.percentage()).isEqualByComparingTo("100.00");
    }

    @Test
    void grade_mcqWrongOption_isIncorrectAndAwardsZero() {
        QuizQuestion q = mcq(1L, 10, 1);
        GradingResult result = gradingService.grade(List.of(q), Map.of(1L, choice(1L, 0)));

        assertThat(result.answers().get(0).getIsCorrect()).isFalse();
        assertThat(result.answers().get(0).getMarksAwarded()).isEqualByComparingTo("0");
    }

    @Test
    void grade_multiSelectPartialMatch_isIncorrect() {
        // Correct is {0, 2}; student selects only {0} — a partial match is not a match.
        QuizQuestion q = multiSelect(1L, 10, 0, 2);
        GradingResult result = gradingService.grade(List.of(q), Map.of(1L, choice(1L, 0)));

        assertThat(result.answers().get(0).getIsCorrect()).isFalse();
    }

    @Test
    void grade_multiSelectExactSetMatchRegardlessOfOrder_isCorrect() {
        QuizQuestion q = multiSelect(1L, 10, 0, 2);
        GradingResult result = gradingService.grade(List.of(q), Map.of(1L, choice(1L, 2, 0)));

        assertThat(result.answers().get(0).getIsCorrect()).isTrue();
        assertThat(result.answers().get(0).getMarksAwarded()).isEqualByComparingTo("10");
    }

    @Test
    void grade_multiSelectExtraWrongOptionSelected_isIncorrect() {
        // Correct is {0, 2}; student selects {0, 1, 2} — an extra wrong pick still fails it.
        QuizQuestion q = multiSelect(1L, 10, 0, 2);
        GradingResult result = gradingService.grade(List.of(q), Map.of(1L, choice(1L, 0, 1, 2)));

        assertThat(result.answers().get(0).getIsCorrect()).isFalse();
    }

    @Test
    void grade_unansweredMcq_isIncorrectNotNull() {
        QuizQuestion q = mcq(1L, 10, 1);
        GradingResult result = gradingService.grade(List.of(q), Map.of());

        assertThat(result.answers().get(0).getIsCorrect()).isFalse();
        assertThat(result.answers().get(0).getGivenAnswer()).isNull();
        assertThat(result.autoGradedMarks()).isEqualByComparingTo("0");
    }

    @Test
    void grade_codeQuestion_staysUngradedAndExcludedFromDenominator() {
        QuizQuestion mcqQ = mcq(1L, 10, 1);
        QuizQuestion codeQ = code(2L, 20);
        SubmitAnswerRequest codeAnswer = new SubmitAnswerRequest(2L, null, "print('hello')");

        GradingResult result = gradingService.grade(List.of(mcqQ, codeQ), Map.of(1L, choice(1L, 1), 2L, codeAnswer));

        var codeResult = result.answers().stream().filter(a -> a.getQuestion().getId() == 2L).findFirst().orElseThrow();
        assertThat(codeResult.getIsCorrect()).isNull();
        assertThat(codeResult.getMarksAwarded()).isNull();
        // Only the MCQ's 10 marks count toward the denominator — build-plan.md's own "half CODE"
        // verify scenario, minimal form.
        assertThat(result.autoGradableMarks()).isEqualByComparingTo("10");
        assertThat(result.autoGradedMarks()).isEqualByComparingTo("10");
        assertThat(result.percentage()).isEqualByComparingTo("100.00");
        assertThat(result.hasCodeQuestions()).isTrue();
    }

    @Test
    void grade_allCodeQuestions_percentageStaysNullNotDivideByZero() {
        QuizQuestion codeQ = code(1L, 20);
        GradingResult result = gradingService.grade(List.of(codeQ), Map.of());

        assertThat(result.autoGradableMarks()).isEqualByComparingTo("0");
        assertThat(result.percentage()).isNull();
    }

    @Test
    void applyResult_percentageAboveThreshold_marksPassed() {
        Quiz quiz = new Quiz();
        quiz.setPassPercentage(60);
        QuizAttempt attempt = new QuizAttempt();

        GradingResult result = new GradingResult(List.of(), BigDecimal.valueOf(70), BigDecimal.valueOf(100),
                BigDecimal.valueOf(70), false);
        gradingService.applyResult(attempt, result, quiz);

        assertThat(attempt.getPassed()).isTrue();
    }

    @Test
    void applyResult_percentageBelowThreshold_marksNotPassed() {
        Quiz quiz = new Quiz();
        quiz.setPassPercentage(60);
        QuizAttempt attempt = new QuizAttempt();

        GradingResult result = new GradingResult(List.of(), BigDecimal.valueOf(50), BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), false);
        gradingService.applyResult(attempt, result, quiz);

        assertThat(attempt.getPassed()).isFalse();
    }

    @Test
    void applyResult_nullPercentage_leavesPassedNull() {
        Quiz quiz = new Quiz();
        quiz.setPassPercentage(60);
        QuizAttempt attempt = new QuizAttempt();

        GradingResult result = new GradingResult(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, null, true);
        gradingService.applyResult(attempt, result, quiz);

        assertThat(attempt.getPassed()).isNull();
    }
}
