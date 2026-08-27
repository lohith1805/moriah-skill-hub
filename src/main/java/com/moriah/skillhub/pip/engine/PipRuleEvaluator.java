package com.moriah.skillhub.pip.engine;

import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;

import java.util.Optional;

/** One evaluator per {@link PipRuleCode} (six concrete classes in this package, architecture.md's
 * package diagram). {@code PipEvaluationService} auto-wires every bean implementing this interface
 * via {@code List<PipRuleEvaluator>} and sorts them by {@link PipRuleCode#ordinal()} before
 * evaluating — Spring gives no ordering guarantee for an injected list, so relying on declaration
 * order there would be fragile; sorting by the enum's own fixed order (build-plan.md's rule table
 * order) makes "first match wins when a student trips more than one rule at once" deterministic. */
public interface PipRuleEvaluator {

    PipRuleCode ruleCode();

    /** Empty if the rule does not trigger for this student (including when the metric column
     * itself is {@code null} — an unset denominator is "no data," never "clean"). A present value
     * is the human-readable {@code trigger_reason} text {@code PipEvaluationService} writes onto
     * the new {@code pip_records} row verbatim. */
    Optional<String> evaluate(StudentMetricProjection metrics, PipRule rule);
}
