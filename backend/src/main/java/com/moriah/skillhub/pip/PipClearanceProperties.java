package com.moriah.skillhub.pip;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code minTaskCompletionPercent} — {@code POST /pip/{id}/review}'s clearance gate (build-plan.md
 * feature 17: "clearance requires task completion ≥ 85%"). {@code minAttendancePercent} is the
 * advisory attendance bar the recovery-progress panel shows against (not part of the hard
 * CLEARED gate, which stays task-completion + weekly-review only). Externalized rather than the hardcoded
 * {@code Constants.PIP_CLEARANCE_MIN_TASK_COMPLETION_PERCENT} an earlier draft used — a `/review`
 * finding against AGENTS.md's "PIP thresholds are rows in pip_rules, not Java" rule. This isn't
 * one of the six {@code pip_rules} rows (that table has no seventh "clearance" code, and none of
 * the six evaluators own this value), so it can't live there either; a {@code
 * @ConfigurationProperties} record is the same "config, not a deployment" relief this codebase
 * already gives {@code TwoFactorProperties}/{@code RateLimitProperties}/{@code CacheProperties} —
 * editable per environment without a recompile, even if not through {@code PUT /pip/rules/{code}}
 * specifically.
 */
@ConfigurationProperties(prefix = "moriah.pip.clearance")
public record PipClearanceProperties(int minTaskCompletionPercent, int minAttendancePercent) {
}
