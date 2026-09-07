package com.moriah.skillhub.hr.dto;

/**
 * One staff member's attendance roll-up for a month ({@code GET /api/v1/hr/attendance/summary}).
 * {@code attendancePct} = (present + late) / (present + late + absent + half_day) * 100, rounded;
 * {@code null} when there is nothing recorded for that person in the month. {@code ON_LEAVE} days
 * are reported but excluded from the percentage — approved leave shouldn't count against a rate.
 */
public record StaffAttendanceSummaryRow(
        String userUuid,
        String userFullName,
        String department,
        long presentDays,
        long lateDays,
        long absentDays,
        long halfDays,
        long onLeaveDays,
        Integer attendancePct
) {
}
