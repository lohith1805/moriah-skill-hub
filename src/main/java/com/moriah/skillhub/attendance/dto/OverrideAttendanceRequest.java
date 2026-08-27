package com.moriah.skillhub.attendance.dto;

import com.moriah.skillhub.attendance.entity.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** build-plan.md feature 13: "PM override on any status" — upserts (`/architect feature 13`
 * decision): creates a row if the student never checked in, updates one if they did. Either way
 * {@code AttendanceService#override} audits old value -> new value via {@code AuditLogService}. */
public record OverrideAttendanceRequest(
        @NotBlank String userUuid,
        @NotNull AttendanceStatus status,
        @Size(max = 2000) String blockerNotes
) {
}
