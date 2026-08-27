package com.moriah.skillhub.pip.engine;

import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import com.moriah.skillhub.pip.entity.PipSeverity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** One test class for all six evaluators — each is a handful of lines wrapping a single
 * comparison against one {@link StudentMetricProjection} column, so covering them together avoids
 * six near-empty boilerplate files while still exercising every rule's trigger/no-trigger/null
 * boundary. */
class PipRuleEvaluatorsTest {

    private static PipRule rule(PipRuleCode code, String threshold) {
        PipRule rule = new PipRule();
        rule.setRuleCode(code);
        rule.setThresholdValue(new BigDecimal(threshold));
        rule.setSeverity(PipSeverity.MEDIUM);
        rule.setActive(true);
        return rule;
    }

    private static StudentMetricProjection metrics(BigDecimal attendancePercent, Integer tasksOverdue48h,
            BigDecimal taskCompletionPercent, Integer consecutiveAssignmentsMissed, BigDecimal quizAveragePercent,
            Integer unsatisfactoryReviews, Integer daysSinceLastActivity) {
        return new StudentMetricProjection(1L, 1L, attendancePercent, tasksOverdue48h, taskCompletionPercent,
                consecutiveAssignmentsMissed, quizAveragePercent, unsatisfactoryReviews, daysSinceLastActivity);
    }

    // --- AttendanceRule ---

    @Test
    void attendanceRule_belowThreshold_triggers() {
        var evaluator = new AttendanceRule();
        var metrics = metrics(new BigDecimal("62.00"), null, null, null, null, null, null);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.ATTENDANCE_LOW, "75.00"))).isPresent();
    }

    @Test
    void attendanceRule_atThreshold_doesNotTrigger() {
        var evaluator = new AttendanceRule();
        var metrics = metrics(new BigDecimal("75.00"), null, null, null, null, null, null);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.ATTENDANCE_LOW, "75.00"))).isEmpty();
    }

    @Test
    void attendanceRule_nullPercent_doesNotTrigger() {
        var evaluator = new AttendanceRule();
        var metrics = metrics(null, null, null, null, null, null, null);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.ATTENDANCE_LOW, "75.00"))).isEmpty();
    }

    // --- ProjectDelayRule ---

    @Test
    void projectDelayRule_atOrAboveThreshold_triggers() {
        var evaluator = new ProjectDelayRule();
        var metrics = metrics(null, 1, null, null, null, null, null);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.PROJECT_DELAY, "1.00"))).isPresent();
    }

    @Test
    void projectDelayRule_belowThreshold_doesNotTrigger() {
        var evaluator = new ProjectDelayRule();
        var metrics = metrics(null, 0, null, null, null, null, null);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.PROJECT_DELAY, "1.00"))).isEmpty();
    }

    // --- AssignmentMissedRule ---

    @Test
    void assignmentMissedRule_atOrAboveThreshold_triggers() {
        var evaluator = new AssignmentMissedRule();
        var metrics = metrics(null, null, null, 2, null, null, null);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.ASSIGNMENT_MISSED, "2.00"))).isPresent();
    }

    @Test
    void assignmentMissedRule_belowThreshold_doesNotTrigger() {
        var evaluator = new AssignmentMissedRule();
        var metrics = metrics(null, null, null, 1, null, null, null);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.ASSIGNMENT_MISSED, "2.00"))).isEmpty();
    }

    // --- QuizFailureRule ---

    @Test
    void quizFailureRule_belowThreshold_triggers() {
        var evaluator = new QuizFailureRule();
        var metrics = metrics(null, null, null, null, new BigDecimal("45.00"), null, null);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.QUIZ_FAILURE, "60.00"))).isPresent();
    }

    @Test
    void quizFailureRule_nullAverage_doesNotTrigger() {
        var evaluator = new QuizFailureRule();
        var metrics = metrics(null, null, null, null, null, null, null);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.QUIZ_FAILURE, "60.00"))).isEmpty();
    }

    // --- ReviewFailedRule ---

    @Test
    void reviewFailedRule_atOrAboveThreshold_triggers() {
        var evaluator = new ReviewFailedRule();
        var metrics = metrics(null, null, null, null, null, 1, null);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.REVIEW_FAILED, "1.00"))).isPresent();
    }

    @Test
    void reviewFailedRule_zero_doesNotTrigger() {
        var evaluator = new ReviewFailedRule();
        var metrics = metrics(null, null, null, null, null, 0, null);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.REVIEW_FAILED, "1.00"))).isEmpty();
    }

    // --- TaskAbandonedRule ---

    @Test
    void taskAbandonedRule_atOrAboveThreshold_triggers() {
        var evaluator = new TaskAbandonedRule();
        var metrics = metrics(null, null, null, null, null, null, 3);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.TASK_ABANDONED, "3.00"))).isPresent();
    }

    @Test
    void taskAbandonedRule_belowThreshold_doesNotTrigger() {
        var evaluator = new TaskAbandonedRule();
        var metrics = metrics(null, null, null, null, null, null, 2);

        assertThat(evaluator.evaluate(metrics, rule(PipRuleCode.TASK_ABANDONED, "3.00"))).isEmpty();
    }
}
