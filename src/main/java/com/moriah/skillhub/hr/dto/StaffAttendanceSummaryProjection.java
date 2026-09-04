package com.moriah.skillhub.hr.dto;

/** Interface projection for {@code StaffAttendanceRepository.summarise} — per-user status counts
 * over a date range. {@code StaffAttendanceService} turns these into {@link StaffAttendanceSummaryRow}
 * with the employee's name / department attached. */
public interface StaffAttendanceSummaryProjection {

    Long getUserId();

    long getPresentDays();

    long getLateDays();

    long getAbsentDays();

    long getHalfDays();

    long getOnLeaveDays();
}
