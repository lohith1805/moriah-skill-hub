package com.moriah.skillhub.pip.engine;

import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** {@code ASSIGNMENT_MISSED} — build-plan.md feature 17: {@code consecutive_assignments_missed
 * >= threshold} (default 2). */
@Component
public class AssignmentMissedRule implements PipRuleEvaluator {

    @Override
    public PipRuleCode ruleCode() {
        return PipRuleCode.ASSIGNMENT_MISSED;
    }

    @Override
    public Optional<String> evaluate(StudentMetricProjection metrics, PipRule rule) {
        int missed = metrics.consecutiveAssignmentsMissed() == null ? 0 : metrics.consecutiveAssignmentsMissed();
        if (BigDecimal.valueOf(missed).compareTo(rule.getThresholdValue()) < 0) {
            return Optional.empty();
        }
        return Optional.of("%d consecutive weekly assignment(s) missed (threshold: %s)."
                .formatted(missed, rule.getThresholdValue()));
    }
}
