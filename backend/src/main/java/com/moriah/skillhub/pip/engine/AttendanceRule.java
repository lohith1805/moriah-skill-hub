package com.moriah.skillhub.pip.engine;

import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** {@code ATTENDANCE_LOW} — build-plan.md feature 17: {@code attendance_percent < threshold}
 * (default 75%). A {@code null} percentage (no attendance rows in the rolling window yet — see
 * {@code StudentMetricsService.Accumulator#attendancePercent}) means no data, not a clean record,
 * so it never triggers. */
@Component
public class AttendanceRule implements PipRuleEvaluator {

    @Override
    public PipRuleCode ruleCode() {
        return PipRuleCode.ATTENDANCE_LOW;
    }

    @Override
    public Optional<String> evaluate(StudentMetricProjection metrics, PipRule rule) {
        if (metrics.attendancePercent() == null) {
            return Optional.empty();
        }
        if (metrics.attendancePercent().compareTo(rule.getThresholdValue()) >= 0) {
            return Optional.empty();
        }
        return Optional.of("Attendance is %s%%, below the %s%% threshold."
                .formatted(metrics.attendancePercent(), rule.getThresholdValue()));
    }
}
