package com.moriah.skillhub.client.dto;

/** {@code GET /api/v1/ba/meetings/staff-directory?role=} — powers the role -> employee cascading
 * picker for "Client Pre-Project Discussions" attendees: pick a role first, then check off the
 * specific people to invite from that role's active roster. */
public record StaffDirectoryEntryResponse(
        String uuid,
        String fullName,
        String email
) {
}
