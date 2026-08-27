package com.moriah.skillhub.pip.engine;

import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** {@code PROJECT_DELAY} — build-plan.md feature 17: {@code tasks_overdue_48h >= threshold}
 * (default 1). Clears the feature 11 stub: a triggered record here is the one whose {@code
 * blocks_task_pull = true} {@code TaskPullGuard} enforces. */
@Component
public class ProjectDelayRule implements PipRuleEvaluator {

    @Override
    public PipRuleCode ruleCode() {
        return PipRuleCode.PROJECT_DELAY;
    }

    @Override
    public Optional<String> evaluate(StudentMetricProjection metrics, PipRule rule) {
        int overdue = metrics.tasksOverdue48h() == null ? 0 : metrics.tasksOverdue48h();
        if (BigDecimal.valueOf(overdue).compareTo(rule.getThresholdValue()) < 0) {
            return Optional.empty();
        }
        return Optional.of("%d committed task(s) are more than 48 hours overdue (threshold: %s)."
                .formatted(overdue, rule.getThresholdValue()));
    }
}
