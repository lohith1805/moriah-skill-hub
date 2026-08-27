package com.moriah.skillhub.hr.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public record GeneratePayrollRequest(
        @NotNull LocalDate periodMonth,
        @NotNull @Min(1) Integer workingDays,
        @NotEmpty @Valid List<PayrollLineRequest> lines
) {
    public GeneratePayrollRequest {
        if (periodMonth != null) {
            periodMonth = periodMonth.withDayOfMonth(1);
        }
        if (lines != null) {
            long distinctEmployeeCount = lines.stream().map(PayrollLineRequest::employeeId)
                    .collect(Collectors.toSet()).size();
            if (distinctEmployeeCount != lines.size()) {
                throw new IllegalArgumentException("lines contains the same employeeId more than once");
            }
        }
    }
}
