package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.EmploymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code PUT /api/v1/hr/employees/{id}} — HR fills in / corrects an employee record. The
 * {@code employeeCode} and the linked user are identity and not editable here. Same "exactly one
 * of baseSalary / hourlyRate" rule as {@link CreateEmployeeRequest}.
 * <p>
 * {@code confirm} = the "Approve" action: when true, this also flips a {@code PENDING_HR} record
 * to {@code CONFIRMED} in the same call (the review form and the approval are one step for HR).
 */
public record UpdateEmployeeRequest(
        @NotBlank @Size(max = 100) String department,
        @NotBlank @Size(max = 100) String designation,
        @NotNull EmploymentType employmentType,
        @NotNull LocalDate dateOfJoining,
        @DecimalMin(value = "0.00") BigDecimal baseSalary,
        @DecimalMin(value = "0.00") BigDecimal hourlyRate,
        Long reportingManagerId,
        boolean confirm
) {
    public UpdateEmployeeRequest {
        if ((baseSalary == null) == (hourlyRate == null)) {
            throw new IllegalArgumentException("Exactly one of baseSalary or hourlyRate must be provided");
        }
    }
}
