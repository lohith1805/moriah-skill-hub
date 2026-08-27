package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.LeaveStatus;
import com.moriah.skillhub.hr.entity.LeaveType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LeaveRequestResponse(
        Long id,
        String userUuid,
        LeaveType leaveType,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal days,
        String reason,
        LeaveStatus status,
        String approvedByUuid,
        Instant decidedAt
) {
}
