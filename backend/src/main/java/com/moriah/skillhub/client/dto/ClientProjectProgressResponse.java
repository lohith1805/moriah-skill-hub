package com.moriah.skillhub.client.dto;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/v1/clients/projects/{id}/progress} — build-plan.md feature 21: "burndown and
 * milestone completion for that client's project only... never another client's data, another
 * batch, or any individual student's PIP status or scores." Derived entirely from real {@code
 * Sprint} rows via {@code SprintService#progressForBatch} (a cross-module service-interface
 * call, never {@code sprint}'s repositories/entities directly — same boundary {@code
 * CertificateService}'s own three eligibility checks already establish). No student name, score,
 * or PIP field appears anywhere in this shape, by construction — it is never read in the first
 * place.
 * <p>
 * {@code milestoneCompletionFraction} is "fraction of this batch's sprints in COMPLETED status"
 * (build-plan.md feature 21 decision); {@code burndown} is planned-vs-completed story points per
 * sprint, reusing {@code Sprint.plannedPoints}/{@code completedPoints} — the same rollup {@code
 * TaskService#completeTask} already maintains for {@code SprintService#totalVelocity}, rather
 * than a second, independent aggregate query over {@code tasks} (the "reasonable, non-
 * overengineered interpretation" this feature's own decision calls for).
 */
public record ClientProjectProgressResponse(
        Long clientProjectId,
        String title,
        Long targetBatchId,
        double milestoneCompletionFraction,
        List<SprintBurndown> burndown
) {

    /** {@code sprintStatus} is a plain {@code String} ({@code SprintStatus.name()}), not the
     * {@code sprint} module's own enum type — this response contract stays independent of
     * {@code sprint}'s internal types, the same decoupling every other cross-module response in
     * this codebase already follows. {@code taskStatusCounts} (FRS MSH-FR-PM-02/MSH-FR-BA-03) is
     * keyed by {@code TaskStatus.name()} for the same reason; a status absent from the map means
     * zero tasks in that state for this sprint, not an error — callers should read it with a
     * default of 0, not assume every status key is present. */
    public record SprintBurndown(
            Long sprintId,
            Integer sprintNumber,
            String sprintStatus,
            int plannedPoints,
            int completedPoints,
            Map<String, Long> taskStatusCounts
    ) {
    }

    /** {@code target_batch_id IS NULL} — "no batch allocated yet" (build-plan.md feature 21
     * decision: "return an empty/zeroed progress shape, not an error"). */
    public static ClientProjectProgressResponse empty(Long clientProjectId, String title) {
        return new ClientProjectProgressResponse(clientProjectId, title, null, 0.0, List.of());
    }
}
