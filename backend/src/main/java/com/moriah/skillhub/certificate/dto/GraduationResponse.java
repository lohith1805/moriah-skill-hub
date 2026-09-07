package com.moriah.skillhub.certificate.dto;

import java.time.Instant;

/** {@code POST /api/v1/batches/{id}/students/{userUuid}/graduate}. Built by {@code
 * GraduationService} from {@code BatchService#graduate}'s {@code GraduationResult} plus the
 * batch's own name (already available via {@code BatchService#get}) — see both services' Javadoc
 * for why the graduation write itself lives in {@code batch/}, not here. */
public record GraduationResponse(
        Long batchId,
        String batchName,
        String userUuid,
        String userFullName,
        Instant graduatedAt
) {
}
