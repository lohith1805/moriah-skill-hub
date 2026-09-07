package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.StaffAttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;

/** One {@code staff_attendance} row (V36). {@code markedByUuid} non-null = an HR user recorded or
 * overrode it rather than the staff member checking in themselves. */
public record StaffAttendanceResponse(
        Long id,
        String userUuid,
        String userFullName,
        LocalDate workDate,
        Instant checkedInAt,
        Instant checkedOutAt,
        StaffAttendanceStatus status,
        String device,
        String markedByUuid,
        String notes,
        Instant createdAt
) {
}
