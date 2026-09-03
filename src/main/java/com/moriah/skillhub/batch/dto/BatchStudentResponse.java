package com.moriah.skillhub.batch.dto;

import com.moriah.skillhub.batch.entity.BatchStudentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of a batch's roster — {@code GET /api/v1/batches/{id}/students}. Carries the enrolled
 * user's {@code uuid} (the public identifier every other endpoint keys off) so a PM screen can
 * drive {@code POST /tasks/{id}/assign}, {@code .../students/{userUuid}/graduate},
 * {@code POST /hr/letters/{type}} etc. without a second lookup. PM/ADMIN only, so the email is
 * safe to include here.
 */
public record BatchStudentResponse(
        String userUuid,
        String fullName,
        String email,
        BatchStudentStatus status,
        Instant joinedAt,
        Instant graduatedAt,
        BigDecimal finalScore
) {
}
