package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.hr.dto.CreateEmployeeRequest;
import com.moriah.skillhub.hr.dto.EmployeeResponse;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.EmployeeStatus;
import com.moriah.skillhub.hr.entity.EmploymentType;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import com.moriah.skillhub.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** build-plan.md feature 19: "Employee records for internal staff and internship-track
 * students" — {@code userUuid} works for either population since both are just {@code users}
 * rows. No update/list endpoint exists in this feature's 8-endpoint list; other HR services
 * (leave, payroll, letters) look employees up directly via {@link
 * com.moriah.skillhub.hr.repository.EmployeeRepository}, not through this class. */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

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

    /**
     * Creates a bare {@code employees} row for a staff member who just accepted their invite
     * ({@link com.moriah.skillhub.hr.StaffEmployeeProvisioningListener}), so attendance check-in
     * and Apply-for-Leave — both of which hard-require an {@code employees} row — work for them
     * from day one instead of 404-ing until HR onboards them by hand.
     * <p>
     * Only the NOT NULL columns are filled, from role-based defaults: an auto {@code EMP-####}
     * code, a department / designation guess, {@code FULL_TIME}, joining = today, and a
     * placeholder {@code 0.00} in whichever compensation column the role implies (trainers are
     * hourly, everyone else salaried — {@code chk_employees_compensation} needs exactly one).
     * HR corrects the real figures, reporting manager, etc. afterwards. Idempotent: a user who
     * already has a row (manual onboarding, a redelivered event) is left untouched.
     */
    @Transactional
    public void autoProvisionForStaff(Long userId) {
        if (employeeRepository.existsByUserId(userId)) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        RoleProfile profile = RoleProfile.forRoles(userRoleRepository.findRoleCodesByUserId(userId));
        if (profile == null) {
            // No staff role we onboard (a STUDENT / CLIENT-only account) — nothing to do.
            return;
        }

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setEmployeeCode(uniqueEmployeeCode(userId));
        employee.setDepartment(profile.department());
        employee.setDesignation(profile.designation());
        employee.setEmploymentType(EmploymentType.FULL_TIME);
        employee.setDateOfJoining(LocalDate.now());
        if (profile.hourly()) {
            employee.setHourlyRate(new BigDecimal("0.00"));
        } else {
            employee.setBaseSalary(new BigDecimal("0.00"));
        }
        employee.setStatus(EmployeeStatus.ACTIVE);
        employeeRepository.save(employee);

        log.info("[hr] auto-provisioned {} ({} / {}) for user {} on invite accept — "
                        + "HR still needs to set real compensation and reporting manager",
                employee.getEmployeeCode(), profile.department(), profile.designation(), userId);
    }

    /** {@code EMP-<userId>}, widened to 4 digits, with a {@code -2}, {@code -3}… suffix on the
     * (near-impossible) chance HR already hand-assigned that exact code to someone else. */
    private String uniqueEmployeeCode(Long userId) {
        String base = String.format("EMP-%04d", userId);
        if (!employeeRepository.existsByEmployeeCode(base)) {
            return base;
        }
        for (int i = 2; i < 100; i++) {
            String candidate = base + "-" + i;
            if (!employeeRepository.existsByEmployeeCode(candidate)) {
                return candidate;
            }
        }
        return "EMP-" + userId + "-" + System.currentTimeMillis();
    }

    /** Per-role defaults for an auto-provisioned record. First match wins, HR-most to
     * least-specific, with ADMIN last since it is usually a second role on top of a real one. */
    private record RoleProfile(String department, String designation, boolean hourly) {
        static RoleProfile forRoles(List<RoleCode> roles) {
            if (roles.contains(RoleCode.HR_MANAGER))       return new RoleProfile("People", "HR Manager", false);
            if (roles.contains(RoleCode.TRAINER_PM))       return new RoleProfile("Training", "Trainer / Program Manager", true);
            if (roles.contains(RoleCode.BUSINESS_ANALYST)) return new RoleProfile("Product", "Business Analyst", false);
            if (roles.contains(RoleCode.DEVELOPER))        return new RoleProfile("Engineering", "Developer", false);
            if (roles.contains(RoleCode.LEAD_GEN))         return new RoleProfile("Growth", "Lead Generation Executive", false);
            if (roles.contains(RoleCode.ADMIN))            return new RoleProfile("Administration", "Administrator", false);
            return null;
        }
    }

    /** {@code GET /api/v1/hr/employees} — feature 19 shipped only create; the FE's HR workspace
     * needs the directory too. All filters optional. */
    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(EmployeeStatus status, String department, String search, Pageable pageable) {
        return PageResponse.from(employeeRepository.search(status, blankToNull(department), blankToNull(search), pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        return toResponse(employeeRepository.findWithAssociationsById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, id)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
