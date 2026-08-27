package com.moriah.skillhub.pip.engine;

import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** {@code TASK_ABANDONED} — build-plan.md feature 17: {@code days_since_last_activity >=
 * threshold} (default 3). Never {@code null} in practice — {@code StudentMetricsService} always
 * seeds a fallback "last activity" from {@code batch_students.joined_at} — but guarded the same
 * way as the other nullable-metric rules regardless, since nothing guarantees that stays true. */
@Component
public class TaskAbandonedRule implements PipRuleEvaluator {

    @Override
    public PipRuleCode ruleCode() {
        return PipRuleCode.TASK_ABANDONED;
    }

    @Override
    public Optional<String> evaluate(StudentMetricProjection metrics, PipRule rule) {
        if (metrics.daysSinceLastActivity() == null) {
            return Optional.empty();
        }
        if (BigDecimal.valueOf(metrics.daysSinceLastActivity()).compareTo(rule.getThresholdValue()) < 0) {
            return Optional.empty();
        }
        return Optional.of("No recorded activity for %d day(s) (threshold: %s)."
                .formatted(metrics.daysSinceLastActivity(), rule.getThresholdValue()));
    }
}
