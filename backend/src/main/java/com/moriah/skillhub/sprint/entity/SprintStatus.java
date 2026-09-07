package com.moriah.skillhub.sprint.entity;

/** Matches V6's {@code chk_sprints_status} CHECK constraint exactly. Forward-only — see
 * {@code SprintService.transitionStatus} for the exact transition table (build-plan.md feature
 * 11: "One ACTIVE sprint per batch; activating requires the previous COMPLETED."). */
public enum SprintStatus {
    PLANNED,
    ACTIVE,
    COMPLETED
}
