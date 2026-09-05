package com.moriah.skillhub.client.dto;

import com.moriah.skillhub.client.entity.BaMeetingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** {@code PUT /api/v1/ba/meetings/{id}} (gap B1.14). Full-field replace. {@code status} and
 * {@code minutes} are settable here — mark a meeting {@code COMPLETED} and attach its write-up in
 * one call. {@code DELETE} is the shortcut for {@code CANCELLED}. {@code attendeeUuids}: {@code
 * null} leaves the invite list untouched; a non-null list (including empty) replaces it wholesale
 * — same "null = don't touch" convention {@code UpdateLeadCampaignRequest.leadIds} already uses.
 * Newly-added attendees get emailed; already-invited ones are not re-notified on every edit. */
public record UpdateBaMeetingRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String agenda,
        Long clientProjectId,
        @NotNull Instant scheduledAt,
        @Positive Integer durationMinutes,
        @Size(max = 255) String location,
        @NotNull BaMeetingStatus status,
        @Size(max = 20000) String minutes,
        List<String> attendeeUuids
) {
}
