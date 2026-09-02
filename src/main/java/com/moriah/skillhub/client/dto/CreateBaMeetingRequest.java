package com.moriah.skillhub.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** {@code POST /api/v1/ba/meetings} (gap B1.14). A new meeting is always {@code SCHEDULED};
 * {@code minutes} is not accepted here — it is written after the meeting via {@code PUT}. */
public record CreateBaMeetingRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String agenda,
        Long clientProjectId,
        @NotNull Instant scheduledAt,
        @Positive Integer durationMinutes,
        @Size(max = 255) String location
) {
}
