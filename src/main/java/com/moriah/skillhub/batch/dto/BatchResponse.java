package com.moriah.skillhub.batch.dto;

import com.moriah.skillhub.batch.entity.BatchStatus;

import java.time.LocalDate;

/** {@code pmUuid}/{@code pmFullName}, never a raw {@code pm_id} — the {@code users.id} invariant
 * (User.java's Javadoc) applies regardless of which entity is doing the exposing. */
public record BatchResponse(
        Long id,
        String name,
        String trackCode,
        String pmUuid,
        String pmFullName,
        String planTierMinCode,
        LocalDate startDate,
        LocalDate endDate,
        Integer capacity,
        Integer enrolledCount,
        BatchStatus status
) {
}
