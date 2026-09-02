package com.moriah.skillhub.interview.dto;

import com.moriah.skillhub.interview.entity.InterviewMode;
import com.moriah.skillhub.interview.entity.InterviewStatus;
import com.moriah.skillhub.interview.entity.InterviewType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * {@code PUT /api/v1/interviews/{id}} (gap B1.8). Full-field replace. Reschedule, change mode,
 * mark {@code COMPLETED}/{@code NO_SHOW} and attach {@code feedback} + {@code rating} (1–10) in
 * one call. The student is not editable — a wrong student means a new interview. {@code DELETE}
 * is the shortcut for {@code CANCELLED}.
 */
public record UpdateInterviewRequest(
        @NotNull InterviewType interviewType,
        @NotNull Instant scheduledAt,
        @Positive Integer durationMinutes,
        InterviewMode mode,
        @Size(max = 255) String location,
        @Size(max = 150) String interviewerName,
        @Size(max = 1000) String meetingLink,
        @NotNull InterviewStatus status,
        @Size(max = 20000) String feedback,
        @Min(1) @Max(10) Integer rating
) {
}
