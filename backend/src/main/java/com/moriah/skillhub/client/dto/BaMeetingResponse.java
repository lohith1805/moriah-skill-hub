package com.moriah.skillhub.client.dto;

import com.moriah.skillhub.client.entity.BaMeetingStatus;

import java.time.Instant;
import java.util.List;

/** A BA meeting ("Client Pre-Project Discussions" in the frontend, gap B1.14). {@code
 * clientProjectId} is {@code null} for a meeting not tied to a specific project. {@code minutes}
 * is {@code null} until written up. {@code attendees} is who was invited — each one is emailed
 * and sees this meeting under their own "Client Pre-Project Discussions" dashboard section (see
 * {@code BaMeetingService#myMeetings}). */
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
        Instant updatedAt,
        List<MeetingAttendeeResponse> attendees
) {
}
