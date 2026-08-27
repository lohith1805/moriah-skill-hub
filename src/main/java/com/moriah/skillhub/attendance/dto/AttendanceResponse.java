package com.moriah.skillhub.attendance.dto;

import com.moriah.skillhub.attendance.entity.AttendanceStatus;

import java.time.Instant;

public record AttendanceResponse(
        Long id,
        Long standupId,
        Long batchId,
        Instant standupScheduledAt,
        String userUuid,
        String userFullName,
        AttendanceStatus status,
        Instant checkedInAt,
        String blockerNotes,
        String markedByUuid,
        boolean autoMarked
) {
}
