package com.moriah.skillhub.hr.dto;

import com.moriah.skillhub.hr.entity.EmployeeProvisioningStatus;
import com.moriah.skillhub.hr.entity.EmployeeStatus;
import com.moriah.skillhub.hr.entity.EmploymentType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
        String userUuid,
        String fullName,
        String employeeCode,
        String department,
        String designation,
        EmploymentType employmentType,
        LocalDate dateOfJoining,
        LocalDate dateOfExit,
        BigDecimal baseSalary,
        BigDecimal hourlyRate,
        Long reportingManagerId,
        EmployeeStatus status,
        EmployeeProvisioningStatus provisioningStatus
) {
}
