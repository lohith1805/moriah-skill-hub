package com.moriah.skillhub.hr.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** {@code sessionHours} is required only for an hourly-rate employee (validated in {@code
 * PayrollService} against the employee's own compensation type, not here — this DTO alone can't
 * know which kind of employee {@code employeeId} refers to). */
public record PayrollLineRequest(
        @NotNull Long employeeId,
        @NotNull @Min(0) Integer presentDays,
        @DecimalMin(value = "0.00") BigDecimal sessionHours,
        @DecimalMin(value = "0.00") BigDecimal deductions
) {
}
