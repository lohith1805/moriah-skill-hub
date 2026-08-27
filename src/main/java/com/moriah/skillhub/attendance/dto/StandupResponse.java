package com.moriah.skillhub.attendance.dto;

import com.moriah.skillhub.attendance.entity.StandupStatus;

import java.time.Instant;

/** No {@code conductedBy} field — {@code /review} finding: nothing in this feature ever calls
 * {@code Standup.setConductedBy}. {@code AttendanceFinalisationJob} is the only writer of {@code
 * status = CONDUCTED} and conducts it as a system action, not a PM one (see {@code Standup}'s own
 * Javadoc) — exposing an API field that is permanently {@code null} under the current design is
 * dead surface, not forward compatibility. The entity column stays mapped (the DB column already
 * exists per V7) in case a future PM-initiated "conduct" action is ever added. */
public record StandupResponse(
        Long id,
        Long batchId,
        Long sprintId,
        Instant scheduledAt,
        Integer lateCutoffMinutes,
        String notes,
        StandupStatus status,
        Instant finalisedAt
) {
}
