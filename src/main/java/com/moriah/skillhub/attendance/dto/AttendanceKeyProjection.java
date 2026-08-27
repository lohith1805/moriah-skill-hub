package com.moriah.skillhub.attendance.dto;

/** {@code (standupId, userId)} pair — {@code AttendanceFinalisationJob}'s bulk "who already has a
 * row" read, so the anti-join against enrolled students happens in memory, not one repository
 * call per student (AGENTS.md: "a repository call inside the per-item loop is a defect"). */
public record AttendanceKeyProjection(Long standupId, Long userId) {
}
