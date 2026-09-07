package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.EmploymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Exactly one of {@code baseSalary}/{@code hourlyRate} — build-plan.md's payroll formula is
 * "base salary, or hourly_rate x session_hours for trainers," an either/or by construction,
 * matching {@code chk_employees_compensation} at the DB level.
 * <p>
 * {@code dateOfJoining} is deliberately not restricted to today-or-earlier — HR routinely creates
 * an employee record ahead of an already-confirmed future start date. */
public record CreateEmployeeRequest(
        @NotBlank String userUuid,
        @NotBlank @Size(max = 30) String employeeCode,
        @NotBlank @Size(max = 100) String department,
        @NotBlank @Size(max = 100) String designation,
        @NotNull EmploymentType employmentType,
        @NotNull LocalDate dateOfJoining,
        @DecimalMin(value = "0.00") BigDecimal baseSalary,
        @DecimalMin(value = "0.00") BigDecimal hourlyRate,
        Long reportingManagerId
) {
    public CreateEmployeeRequest {
        if ((baseSalary == null) == (hourlyRate == null)) {
            throw new IllegalArgumentException("Exactly one of baseSalary or hourlyRate must be provided");
        }
    }
}
