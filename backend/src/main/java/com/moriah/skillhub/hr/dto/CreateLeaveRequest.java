package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.LeaveType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateLeaveRequest(
        @NotNull LeaveType leaveType,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        @Size(max = 2000) String reason
) {
    public CreateLeaveRequest {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate must not be before fromDate");
        }
    }
}
