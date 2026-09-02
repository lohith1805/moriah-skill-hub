package com.moriah.skillhub.client.dto;

import com.moriah.skillhub.client.entity.BaMeetingStatus;

import java.time.Instant;

/** A BA meeting (gap B1.14). {@code clientProjectId} is {@code null} for a meeting not tied to a
 * specific project. {@code minutes} is {@code null} until written up. */
public record BaMeetingResponse(
        Long id,
        String title,
        String agenda,
        Long clientProjectId,
        Instant scheduledAt,
        Integer durationMinutes,
        String location,
        BaMeetingStatus status,
        String minutes,
        String createdByUuid,
        Instant createdAt,
        Instant updatedAt
) {
}
