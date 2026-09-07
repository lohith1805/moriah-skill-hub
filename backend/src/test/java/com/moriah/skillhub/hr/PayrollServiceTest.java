package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.hr.dto.GeneratePayrollRequest;
import com.moriah.skillhub.hr.dto.PayrollLineRequest;
import com.moriah.skillhub.hr.dto.PayrollRecordResponse;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.PayrollStatus;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.hr.repository.PayrollRecordRepository;
import com.moriah.skillhub.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock
    private PayrollRecordRepository payrollRecordRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private AuditLogService auditLogService;

    // Built per-test, not as a field initializer — a field initializer runs during construction,
    // before MockitoExtension's beforeEach injects @Mock fields, leaving them null.
    private PayrollService service() {
        return new PayrollService(payrollRecordRepository, employeeRepository, storageService, auditLogService);
    }

    private Employee employee(long id, BigDecimal baseSalary, BigDecimal hourlyRate) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeCode("EMP-00" + id);
        employee.setBaseSalary(baseSalary);
        employee.setHourlyRate(hourlyRate);
        User user = new User();
        user.setId(id);
        user.setFullName("Employee " + id);
        employee.setUser(user);
        return employee;
    }

    @Test
    void generate_salariedEmployee_grossEqualsBaseSalary() throws Exception {
        Employee employee = employee(1L, new BigDecimal("50000.00"), null);
        when(employeeRepository.findAllWithUserByIdIn(List.of(1L))).thenReturn(List.of(employee));
        when(payrollRecordRepository.findEmployeeIdsAlreadyGenerated(LocalDate.of(2026, 3, 1), List.of(1L)))
                .thenReturn(List.of());
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("payslips/EMP-001/2026-03.pdf");
        when(storageService.presignedGetUrl(anyString(), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        GeneratePayrollRequest request = new GeneratePayrollRequest(LocalDate.of(2026, 3, 1), 22,
                List.of(new PayrollLineRequest(1L, 22, null, new BigDecimal("1000.00"))));

        List<PayrollRecordResponse> responses = service().generate(request, "hr-uuid", 9L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).grossAmount()).isEqualByComparingTo("50000.00");
        assertThat(responses.get(0).netAmount()).isEqualByComparingTo("49000.00");
        assertThat(responses.get(0).status()).isEqualTo(PayrollStatus.FINALISED);
    }

    @Test
    void generate_hourlyEmployee_grossEqualsRateTimesHours() throws Exception {
        Employee employee = employee(2L, null, new BigDecimal("500.00"));
        when(employeeRepository.findAllWithUserByIdIn(List.of(2L))).thenReturn(List.of(employee));
        when(payrollRecordRepository.findEmployeeIdsAlreadyGenerated(LocalDate.of(2026, 3, 1), List.of(2L)))
                .thenReturn(List.of());
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("payslips/EMP-002/2026-03.pdf");
        when(storageService.presignedGetUrl(anyString(), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        GeneratePayrollRequest request = new GeneratePayrollRequest(LocalDate.of(2026, 3, 1), 22,
                List.of(new PayrollLineRequest(2L, 20, new BigDecimal("40.00"), null)));

        List<PayrollRecordResponse> responses = service().generate(request, "hr-uuid", 9L);

        assertThat(responses.get(0).grossAmount()).isEqualByComparingTo("20000.00");
        assertThat(responses.get(0).deductions()).isEqualByComparingTo("0.00");
    }

    @Test
    void generate_hourlyEmployeeMissingSessionHours_throwsValidationFailed() {
        Employee employee = employee(2L, null, new BigDecimal("500.00"));
        when(employeeRepository.findAllWithUserByIdIn(List.of(2L))).thenReturn(List.of(employee));
        when(payrollRecordRepository.findEmployeeIdsAlreadyGenerated(LocalDate.of(2026, 3, 1), List.of(2L)))
                .thenReturn(List.of());

        GeneratePayrollRequest request = new GeneratePayrollRequest(LocalDate.of(2026, 3, 1), 22,
                List.of(new PayrollLineRequest(2L, 20, null, null)));

        assertThatThrownBy(() -> service().generate(request, "hr-uuid", 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void generate_presentDaysExceedsWorkingDays_throwsValidationFailed() {
        Employee employee = employee(1L, new BigDecimal("50000.00"), null);
        when(employeeRepository.findAllWithUserByIdIn(List.of(1L))).thenReturn(List.of(employee));
        when(payrollRecordRepository.findEmployeeIdsAlreadyGenerated(LocalDate.of(2026, 3, 1), List.of(1L)))
                .thenReturn(List.of());

        GeneratePayrollRequest request = new GeneratePayrollRequest(LocalDate.of(2026, 3, 1), 22,
                List.of(new PayrollLineRequest(1L, 30, null, null)));

        assertThatThrownBy(() -> service().generate(request, "hr-uuid", 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void generate_deductionsExceedGrossAmount_throwsValidationFailed() {
        Employee employee = employee(1L, new BigDecimal("1000.00"), null);
        when(employeeRepository.findAllWithUserByIdIn(List.of(1L))).thenReturn(List.of(employee));
        when(payrollRecordRepository.findEmployeeIdsAlreadyGenerated(LocalDate.of(2026, 3, 1), List.of(1L)))
                .thenReturn(List.of());

        GeneratePayrollRequest request = new GeneratePayrollRequest(LocalDate.of(2026, 3, 1), 22,
                List.of(new PayrollLineRequest(1L, 22, null, new BigDecimal("5000.00"))));

        assertThatThrownBy(() -> service().generate(request, "hr-uuid", 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void generate_alreadyGeneratedForEmployeeAndMonth_throws409() {
        Employee employee = employee(1L, new BigDecimal("50000.00"), null);
        when(employeeRepository.findAllWithUserByIdIn(List.of(1L))).thenReturn(List.of(employee));
        when(payrollRecordRepository.findEmployeeIdsAlreadyGenerated(LocalDate.of(2026, 3, 1), List.of(1L)))
                .thenReturn(List.of(1L));

        GeneratePayrollRequest request = new GeneratePayrollRequest(LocalDate.of(2026, 3, 1), 22,
                List.of(new PayrollLineRequest(1L, 22, null, null)));

        assertThatThrownBy(() -> service().generate(request, "hr-uuid", 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYROLL_ALREADY_GENERATED);
    }

    @Test
    void list_returnsPageWithPresignedPayslipUrls() throws Exception {
        Employee employee = employee(1L, new BigDecimal("50000.00"), null);
        com.moriah.skillhub.hr.entity.PayrollRecord record = new com.moriah.skillhub.hr.entity.PayrollRecord();
        record.setId(1L);
        record.setEmployee(employee);
        record.setPeriodMonth(LocalDate.of(2026, 3, 1));
        record.setWorkingDays(22);
        record.setPresentDays(22);
        record.setGrossAmount(new BigDecimal("50000.00"));
        record.setDeductions(BigDecimal.ZERO);
        record.setNetAmount(new BigDecimal("50000.00"));
        record.setPayslipKey("payslips/EMP-001/2026-03.pdf");
        record.setStatus(PayrollStatus.FINALISED);
        when(payrollRecordRepository.findByPeriodMonth(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(record)));
        when(storageService.presignedGetUrl(anyString(), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        var page = service().list(LocalDate.of(2026, 3, 1), "hr-uuid",
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).payslipDownloadUrl()).isEqualTo("https://s3.example.com/x");
    }

    @Test
    void generate_unknownEmployee_throwsNotFound() {
        when(employeeRepository.findAllWithUserByIdIn(List.of(1L))).thenReturn(List.of());
        when(payrollRecordRepository.findEmployeeIdsAlreadyGenerated(LocalDate.of(2026, 3, 1), List.of(1L)))
                .thenReturn(List.of());

        GeneratePayrollRequest request = new GeneratePayrollRequest(LocalDate.of(2026, 3, 1), 22,
                List.of(new PayrollLineRequest(1L, 22, null, null)));

        assertThatThrownBy(() -> service().generate(request, "hr-uuid", 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPLOYEE_NOT_FOUND);
    }
}
