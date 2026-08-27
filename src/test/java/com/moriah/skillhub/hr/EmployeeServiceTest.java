package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.hr.dto.CreateEmployeeRequest;
import com.moriah.skillhub.hr.dto.EmployeeResponse;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.EmploymentType;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;

    // Built per-test, not as a field initializer — a field initializer runs during construction,
    // before MockitoExtension's beforeEach injects @Mock fields, leaving them null.
    private EmployeeService service() {
        return new EmployeeService(employeeRepository, userRepository);
    }

    private User user(long id, String uuid) {
        User user = new User();
        user.setId(id);
        user.setUuid(uuid);
        user.setFullName("Employee " + id);
        return user;
    }

    private CreateEmployeeRequest request(BigDecimal baseSalary, BigDecimal hourlyRate, Long managerId) {
        return new CreateEmployeeRequest("user-uuid", "EMP-001", "Engineering", "Trainer",
                EmploymentType.FULL_TIME, LocalDate.now().minusDays(1), baseSalary, hourlyRate, managerId);
    }

    @Test
    void create_happyPath_savesAndReturnsResponse() {
        User user = user(1L, "user-uuid");
        when(userRepository.findByUuid("user-uuid")).thenReturn(Optional.of(user));
        when(employeeRepository.existsByUserId(1L)).thenReturn(false);
        when(employeeRepository.existsByEmployeeCode("EMP-001")).thenReturn(false);

        EmployeeResponse response = service().create(request(new BigDecimal("50000.00"), null, null));

        assertThat(response.userUuid()).isEqualTo("user-uuid");
        assertThat(response.employeeCode()).isEqualTo("EMP-001");
    }

    @Test
    void create_userAlreadyHasEmployeeRecord_throwsAlreadyExists() {
        User user = user(1L, "user-uuid");
        when(userRepository.findByUuid("user-uuid")).thenReturn(Optional.of(user));
        when(employeeRepository.existsByUserId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service().create(request(new BigDecimal("50000.00"), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPLOYEE_ALREADY_EXISTS);
    }

    @Test
    void create_duplicateEmployeeCode_throwsBusinessRuleViolation() {
        User user = user(1L, "user-uuid");
        when(userRepository.findByUuid("user-uuid")).thenReturn(Optional.of(user));
        when(employeeRepository.existsByUserId(1L)).thenReturn(false);
        when(employeeRepository.existsByEmployeeCode("EMP-001")).thenReturn(true);

        assertThatThrownBy(() -> service().create(request(new BigDecimal("50000.00"), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void create_unknownUser_throwsNotFound() {
        when(userRepository.findByUuid("user-uuid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(request(new BigDecimal("50000.00"), null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void create_unknownReportingManager_throwsNotFound() {
        User user = user(1L, "user-uuid");
        when(userRepository.findByUuid("user-uuid")).thenReturn(Optional.of(user));
        when(employeeRepository.existsByUserId(1L)).thenReturn(false);
        when(employeeRepository.existsByEmployeeCode("EMP-001")).thenReturn(false);
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(request(new BigDecimal("50000.00"), null, 99L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPLOYEE_NOT_FOUND);
    }

    @Test
    void create_withReportingManager_linksIt() {
        User user = user(1L, "user-uuid");
        when(userRepository.findByUuid("user-uuid")).thenReturn(Optional.of(user));
        when(employeeRepository.existsByUserId(1L)).thenReturn(false);
        when(employeeRepository.existsByEmployeeCode("EMP-001")).thenReturn(false);
        Employee manager = new Employee();
        manager.setId(5L);
        manager.setUser(user(2L, "manager-uuid"));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(manager));

        EmployeeResponse response = service().create(request(null, new BigDecimal("500.00"), 5L));

        assertThat(response.reportingManagerId()).isEqualTo(5L);
    }
}
