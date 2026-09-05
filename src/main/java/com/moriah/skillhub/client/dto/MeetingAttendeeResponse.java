package com.moriah.skillhub.client.dto;

/** One person invited to a {@code BaMeeting} ("Client Pre-Project Discussions" in the frontend) —
 * see {@link com.moriah.skillhub.client.entity.BaMeetingAttendee}. */
public record MeetingAttendeeResponse(
        String uuid,
        String fullName,
        String email
) {
}
