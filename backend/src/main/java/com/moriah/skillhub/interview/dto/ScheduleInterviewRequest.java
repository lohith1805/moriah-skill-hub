package com.moriah.skillhub.interview.dto;

import com.moriah.skillhub.interview.entity.InterviewMode;
import com.moriah.skillhub.interview.entity.InterviewType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** {@code POST /api/v1/interviews} (gap B1.8) — a TRAINER_PM/ADMIN schedules an interview for a
 * student. A new interview is always {@code SCHEDULED}; {@code feedback}/{@code rating} come
 * later via {@code PUT}. */
public record ScheduleInterviewRequest(
        @NotBlank String studentUuid,
        @NotNull InterviewType interviewType,
        @NotNull Instant scheduledAt,
        @Positive Integer durationMinutes,
        InterviewMode mode,
        @Size(max = 255) String location,
        @Size(max = 150) String interviewerName,
        @Size(max = 1000) String meetingLink
) {
}
