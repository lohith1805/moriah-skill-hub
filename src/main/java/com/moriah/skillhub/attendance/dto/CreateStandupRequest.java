package com.moriah.skillhub.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** {@code lateCutoffMinutes} is optional — {@code StandupService} defaults it to {@code
 * Constants.ATTENDANCE_DEFAULT_LATE_CUTOFF_MINUTES} when {@code null}, matching V7's own column
 * default. {@code scheduledAt} deliberately has no {@code @FutureOrPresent} — a PM logging a
 * standup that already happened this morning is a normal case, not an error. */
public record CreateStandupRequest(
        @NotNull Long batchId,
        Long sprintId,
        @NotNull Instant scheduledAt,
        @Min(1) @Max(180) Integer lateCutoffMinutes,
        @Size(max = 2000) String notes
) {
}
