package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.StaffAttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body of {@code PUT /api/v1/hr/attendance/mark} (HR_MANAGER/ADMIN). Upserts the row for
 * {@code (userUuid, workDate)} — creates one if the staff member never checked in, corrects the
 * status if they did. Audited old-value -> new-value.
 */
public record MarkStaffAttendanceRequest(
        @NotBlank String userUuid,
        @NotNull LocalDate workDate,
        @NotNull StaffAttendanceStatus status,
        @Size(max = 500) String notes
) {
}
