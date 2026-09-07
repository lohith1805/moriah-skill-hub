package com.moriah.skillhub.attendance.dto;

import com.moriah.skillhub.attendance.entity.StandupStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code batchId}/{@code sprintId}/{@code scheduledAt} are immutable after creation — same
 * "identifying fields don't move" reasoning as {@code UpdateSprintRequest}/{@code
 * UpdateTaskRequest}. {@code status} only ever legally moves {@code SCHEDULED -> CANCELLED} here
 * (see {@code StandupService#update}) — {@code CONDUCTED} is never PM-set, only {@code
 * AttendanceFinalisationJob} writes it (`/architect feature 13` decision). */
public record UpdateStandupRequest(
        @Size(max = 2000) String notes,
        @Min(1) @Max(180) Integer lateCutoffMinutes,
        @NotNull StandupStatus status,
        @Size(max = 500) String meetingLink
) {
}
