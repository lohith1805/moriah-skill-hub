package com.moriah.skillhub.pip.dto;

import com.moriah.skillhub.pip.entity.PipSeverity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** {@code PUT /api/v1/pip/rules/{code}} — full replace of every admin-tunable field on an
 * existing rule (build-plan.md: "thresholds are config, not a deployment"). Deliberately has no
 * {@code windowDays} field, unlike an earlier draft — {@code PipRule.windowDays} is stored,
 * genuinely admin-visible via {@code GET /pip/rules}, but never read by any evaluator (V11's own
 * comment on that column explains why). Accepting it here would let an ADMIN believe changing it
 * changes evaluation behavior when it silently doesn't — a `/review` finding against the exact
 * "config that looks live but isn't" anti-pattern feature 16's own review already flagged once. */
public record UpdatePipRuleRequest(
        @NotNull @DecimalMin(value = "0.00") BigDecimal thresholdValue,
        @NotNull PipSeverity severity,
        @NotNull Boolean active
) {
}
