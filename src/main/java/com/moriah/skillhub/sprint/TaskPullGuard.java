package com.moriah.skillhub.sprint;

import com.moriah.skillhub.pip.PipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The {@code pip_records.blocks_task_pull} hook for {@code POST /tasks/{id}/pull} (build-plan.md
 * feature 11's stub, cleared here at feature 17). Delegates to {@link PipService#blocksPull} — a
 * proper cross-package service call (architecture.md: "a feature module may call another module's
 * service interface"), not raw {@code JdbcTemplate} against {@code pip_records} directly. An
 * earlier draft used raw SQL here, citing {@code EntitlementGuard}/{@code OwnershipGuard}'s
 * precedent for that workaround — a `/review` finding pointed out that precedent applies only when
 * the owning feature's service doesn't exist yet (true for those two, in {@code common/security},
 * which can never import a feature package); {@code pip/} already has a full service layer, so a
 * real method call is both cleaner and the pattern this codebase has consistently converged on
 * (see {@code StudentMetricsService}'s own cohort-read fix in feature 16's decision log for the
 * identical correction). Tracked as an Open Stub row in progress-tracker.md ("PIP
 * blocks_task_pull check on POST /tasks/{id}/pull", left in 11, cleared in 17) — now cleared.
 */
@Component
@RequiredArgsConstructor
public class TaskPullGuard {

    private final PipService pipService;

    public boolean blocksPull(Long studentUserId) {
        return pipService.blocksPull(studentUserId);
    }
}
