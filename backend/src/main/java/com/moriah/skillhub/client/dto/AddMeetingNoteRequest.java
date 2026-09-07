package com.moriah.skillhub.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/meetings/{id}/note} — the user's own ask: once a Client Pre-Project
 * Discussion is over, any attendee (not just the BA who scheduled it) can jot down a small note
 * about what it covered. Reuses {@code BaMeeting.minutes} — the same field the BA's own "Log MOM"
 * flow already writes through {@code PUT /ba/meetings/{id}} — there's exactly one note per
 * meeting, whoever writes it last. */
public record AddMeetingNoteRequest(
        @NotBlank @Size(max = 5000) String note
) {
}
