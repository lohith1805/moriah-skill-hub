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

    /** Total minutes between check-in and check-out across the range, counting only days that
     * have both timestamps. Days with a missing check-out contribute nothing — the payroll
     * pre-fill treats this as a starting figure HR reviews, not a final one. */
    Long getWorkedMinutes();
}
