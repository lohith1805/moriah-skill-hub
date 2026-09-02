package com.moriah.skillhub.interview.dto;

import com.moriah.skillhub.interview.entity.InterviewMode;
import com.moriah.skillhub.interview.entity.InterviewStatus;
import com.moriah.skillhub.interview.entity.InterviewType;

import java.time.Instant;

/** One student interview (gap B1.8). {@code feedback} / {@code rating} are {@code null} until the
 * interview is written up. {@code studentUuid} / {@code scheduledByUuid} are public ids. */
public record InterviewResponse(
        Long id,
        String studentUuid,
        String studentName,
        String scheduledByUuid,
        InterviewType interviewType,
        Instant scheduledAt,
        Integer durationMinutes,
        InterviewMode mode,
        String location,
        String interviewerName,
        String meetingLink,
        InterviewStatus status,
        String feedback,
        Integer rating,
        Instant createdAt,
        Instant updatedAt
) {
}
