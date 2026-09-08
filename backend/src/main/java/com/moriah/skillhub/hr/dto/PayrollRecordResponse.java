package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.PayrollStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayrollRecordResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeFullName,
        LocalDate periodMonth,
        Integer workingDays,
        Integer presentDays,
        BigDecimal lopDays,
        BigDecimal sessionHours,
        BigDecimal grossAmount,
        BigDecimal deductions,
        BigDecimal netAmount,
        String payslipDownloadUrl,
        PayrollStatus status
) {
}
