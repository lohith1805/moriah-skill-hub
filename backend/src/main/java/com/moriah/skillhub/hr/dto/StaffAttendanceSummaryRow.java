package com.moriah.skillhub.hr.dto;

import java.math.BigDecimal;

/**
 * One staff member's attendance roll-up for a month ({@code GET /api/v1/hr/attendance/summary}).
 * {@code attendancePct} = (present + late) / (present + late + absent + half_day) * 100, rounded;
 * {@code null} when there is nothing recorded for that person in the month. {@code ON_LEAVE} days
 * are reported but excluded from the percentage — approved leave shouldn't count against a rate.
 * {@code workedHours} sums check-in→check-out over days that have both stamps (2 dp) — the HR
 * payroll screen pre-fills an hourly employee's session hours from it.
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
        Integer attendancePct,
        BigDecimal workedHours
) {
}
