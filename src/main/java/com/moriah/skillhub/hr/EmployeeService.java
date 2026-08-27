package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.hr.dto.CreateEmployeeRequest;
import com.moriah.skillhub.hr.dto.EmployeeResponse;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.EmployeeStatus;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** build-plan.md feature 19: "Employee records for internal staff and internship-track
 * students" — {@code userUuid} works for either population since both are just {@code users}
 * rows. No update/list endpoint exists in this feature's 8-endpoint list; other HR services
 * (leave, payroll, letters) look employees up directly via {@link
 * com.moriah.skillhub.hr.repository.EmployeeRepository}, not through this class. */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        User user = userRepository.findByUuid(request.userUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, request.userUuid()));

        if (employeeRepository.existsByUserId(user.getId())) {
            throw new BusinessException(ErrorCode.EMPLOYEE_ALREADY_EXISTS);
        }
        if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This employee code is already in use.");
        }

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode(request.employeeCode());
        employee.setDepartment(request.department());
        employee.setDesignation(request.designation());
        employee.setEmploymentType(request.employmentType());
        employee.setDateOfJoining(request.dateOfJoining());
        employee.setBaseSalary(request.baseSalary());
        employee.setHourlyRate(request.hourlyRate());
        employee.setStatus(EmployeeStatus.ACTIVE);

        if (request.reportingManagerId() != null) {
            Employee manager = employeeRepository.findById(request.reportingManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, request.reportingManagerId()));
            employee.setReportingManager(manager);
        }

        employeeRepository.save(employee);
        return toResponse(employee);
    }

    private EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getUser().getUuid(),
                employee.getUser().getFullName(),
                employee.getEmployeeCode(),
                employee.getDepartment(),
                employee.getDesignation(),
                employee.getEmploymentType(),
                employee.getDateOfJoining(),
                employee.getDateOfExit(),
                employee.getBaseSalary(),
                employee.getHourlyRate(),
                employee.getReportingManager() == null ? null : employee.getReportingManager().getId(),
                employee.getStatus());
    }
}
