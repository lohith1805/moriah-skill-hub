package com.moriah.skillhub.pip.dto;

import com.moriah.skillhub.pip.entity.PipRuleCode;
import com.moriah.skillhub.pip.entity.PipSeverity;
import com.moriah.skillhub.pip.entity.PipStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** No dedicated {@code GET /pip/{id}} endpoint exists in build-plan.md's feature 17 list — {@code
 * GET /pip/me} and {@code GET /pip?batchId=&status=} are the only read paths, so this carries full
 * detail (nested {@code milestones}), matching {@code ProjectResponse}'s precedent for the same
 * "no separate detail endpoint" shape. */
public record PipRecordResponse(
        Long id,
        String studentUuid,
        String studentFullName,
        Long batchId,
        String batchName,
        PipRuleCode ruleCode,
        String triggerReason,
        PipSeverity severity,
        Instant triggeredAt,
        LocalDate startDate,
        LocalDate endDate,
        PipStatus status,
        boolean blocksTaskPull,
        String reviewedByUuid,
        String reviewNotes,
        Instant outcomeAt,
        List<PipMilestoneResponse> milestones
) {
}
