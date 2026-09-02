package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.hr.dto.CreateEmployeeRequest;
import com.moriah.skillhub.hr.dto.EmployeeResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.EmployeeStatus;
import com.moriah.skillhub.hr.entity.EmploymentType;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

    private Employee employee(long id) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeCode("EMP-00" + id);
        e.setDepartment("Engineering");
        e.setDesignation("Trainer");
        e.setEmploymentType(EmploymentType.FULL_TIME);
        e.setDateOfJoining(LocalDate.of(2026, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setUser(user(id + 100, "u-" + id));
        return e;
    }

    @Test
    void list_passesFiltersThroughAndMapsRows() {
        when(employeeRepository.search(eq(EmployeeStatus.ACTIVE), eq("Engineering"), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee(1L), employee(2L)), PageRequest.of(0, 20), 2));

        PageResponse<EmployeeResponse> page = service().list(
                EmployeeStatus.ACTIVE, "Engineering", null, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(2);
        assertThat(page.content().get(0).employeeCode()).isEqualTo("EMP-001");
        verify(employeeRepository).search(eq(EmployeeStatus.ACTIVE), eq("Engineering"), any(), any(Pageable.class));
    }

    @Test
    void get_unknownId_throwsNotFound() {
        when(employeeRepository.findWithAssociationsById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPLOYEE_NOT_FOUND);
    }

    @Test
    void get_returnsMappedEmployee() {
        when(employeeRepository.findWithAssociationsById(3L)).thenReturn(Optional.of(employee(3L)));

        EmployeeResponse response = service().get(3L);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(EmployeeStatus.ACTIVE);
    }
}
