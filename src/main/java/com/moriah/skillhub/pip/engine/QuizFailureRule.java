package com.moriah.skillhub.pip.engine;

import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** {@code QUIZ_FAILURE} — build-plan.md feature 17: {@code quiz_average_percent < threshold}
 * (default 60%). A {@code null} average (no graded attempts yet — {@code
 * StudentMetricsService#applyQuizzes} excludes ungraded {@code PENDING_MANUAL_GRADING} attempts
 * from the denominator) means no data, never a trigger — mirrors {@code AttendanceRule}. */
@Component
public class QuizFailureRule implements PipRuleEvaluator {

    @Override
    public PipRuleCode ruleCode() {
        return PipRuleCode.QUIZ_FAILURE;
    }

    @Override
    public Optional<String> evaluate(StudentMetricProjection metrics, PipRule rule) {
        if (metrics.quizAveragePercent() == null) {
            return Optional.empty();
        }
        if (metrics.quizAveragePercent().compareTo(rule.getThresholdValue()) >= 0) {
            return Optional.empty();
        }
        return Optional.of("Quiz average is %s%%, below the %s%% threshold."
                .formatted(metrics.quizAveragePercent(), rule.getThresholdValue()));
    }
}
