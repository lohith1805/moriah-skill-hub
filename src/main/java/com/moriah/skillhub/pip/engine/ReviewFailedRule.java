package com.moriah.skillhub.pip.engine;

import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** {@code REVIEW_FAILED} — build-plan.md feature 17: {@code unsatisfactory_reviews >= threshold}
 * (default 1). {@code unsatisfactory_reviews} is lifetime-cumulative within the batch (feature 16's
 * own decision — architecture.md specifies no window for it), so this rule reads a running count,
 * not just reviews from the current week. */
@Component
public class ReviewFailedRule implements PipRuleEvaluator {

    @Override
    public PipRuleCode ruleCode() {
        return PipRuleCode.REVIEW_FAILED;
    }

    @Override
    public Optional<String> evaluate(StudentMetricProjection metrics, PipRule rule) {
        int unsatisfactory = metrics.unsatisfactoryReviews() == null ? 0 : metrics.unsatisfactoryReviews();
        if (BigDecimal.valueOf(unsatisfactory).compareTo(rule.getThresholdValue()) < 0) {
            return Optional.empty();
        }
        return Optional.of("%d unsatisfactory weekly review(s) on record (threshold: %s)."
                .formatted(unsatisfactory, rule.getThresholdValue()));
    }
}
