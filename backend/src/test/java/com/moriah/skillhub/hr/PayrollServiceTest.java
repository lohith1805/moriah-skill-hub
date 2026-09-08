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
import com.moriah.skillhub.hr.entity.LeaveRequest;
import com.moriah.skillhub.hr.entity.LeaveStatus;
import com.moriah.skillhub.hr.entity.LeaveType;
import com.moriah.skillhub.hr.entity.PayrollStatus;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.hr.repository.LeaveRequestRepository;
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
    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    // Built per-test, not as a field initializer — a field initializer runs during construction,
    // before MockitoExtension's beforeEach injects @Mock fields, leaving them null.
    private PayrollService service() {
        return new PayrollService(payrollRecordRepository, employeeRepository, storageService, auditLogService,
                leaveRequestRepository);
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

    private LeaveRequest paidLeave(long userId, LocalDate from, LocalDate to, String days) {
        LeaveRequest leave = new LeaveRequest();
        User user = new User();
        user.setId(userId);
        leave.setUser(user);
        leave.setLeaveType(LeaveType.EARNED); // any of SICK/CASUAL/EARNED — a paid type
        leave.setStatus(LeaveStatus.APPROVED);
        leave.setFromDate(from);
        leave.setToDate(to);
        leave.setDays(new BigDecimal(days));
        return leave;
    }

    @Test
    void generate_salariedFullMonthPresent_grossEqualsBaseSalary() throws Exception {
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
        assertThat(responses.get(0).lopDays()).isEqualByComparingTo("0.0");
        assertThat(responses.get(0).status()).isEqualTo(PayrollStatus.FINALISED);
    }

    /** The key rule: a day not present and not on paid leave is loss of pay, even with no leave
     * record at all. */
    @Test
    void generate_salariedWithAbsentDays_proratesGrossPerWorkingDay() throws Exception {
        Employee employee = employee(1L, new BigDecimal("44000.00"), null); // 2000 / day over 22
        when(employeeRepository.findAllWithUserByIdIn(List.of(1L))).thenReturn(List.of(employee));
        when(payrollRecordRepository.findEmployeeIdsAlreadyGenerated(LocalDate.of(2026, 3, 1), List.of(1L)))
                .thenReturn(List.of());
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("payslips/EMP-001/2026-03.pdf");
        when(storageService.presignedGetUrl(anyString(), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        // present 19/22, no leave of any kind -> 3 absent days docked.
        GeneratePayrollRequest request = new GeneratePayrollRequest(LocalDate.of(2026, 3, 1), 22,
                List.of(new PayrollLineRequest(1L, 19, null, null)));

        List<PayrollRecordResponse> responses = service().generate(request, "hr-uuid", 9L);

        assertThat(responses.get(0).lopDays()).isEqualByComparingTo("3.0");
        assertThat(responses.get(0).grossAmount()).isEqualByComparingTo("38000.00"); // 44000 * 19/22
        assertThat(responses.get(0).netAmount()).isEqualByComparingTo("38000.00");
    }

    @Test
    void generate_salariedWithApprovedPaidLeave_paysThoseDaysInFull() throws Exception {
        Employee employee = employee(1L, new BigDecimal("44000.00"), null);
        when(employeeRepository.findAllWithUserByIdIn(List.of(1L))).thenReturn(List.of(employee));
        when(payrollRecordRepository.findEmployeeIdsAlreadyGenerated(LocalDate.of(2026, 3, 1), List.of(1L)))
                .thenReturn(List.of());
        when(leaveRequestRepository.findApprovedPaidLeaveOverlappingMonth(any(), any(), any()))
                .thenReturn(List.of(paidLeave(1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), "3.0")));
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("payslips/EMP-001/2026-03.pdf");
        when(storageService.presignedGetUrl(anyString(), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        // present 19 + 3 paid leave = 22 payable -> full base, nothing docked.
        GeneratePayrollRequest request = new GeneratePayrollRequest(LocalDate.of(2026, 3, 1), 22,
                List.of(new PayrollLineRequest(1L, 19, null, null)));

        List<PayrollRecordResponse> responses = service().generate(request, "hr-uuid", 9L);

        assertThat(responses.get(0).lopDays()).isEqualByComparingTo("0.0");
        assertThat(responses.get(0).grossAmount()).isEqualByComparingTo("44000.00");
    }

    @Test
    void generate_paidLeaveStraddlingMonthBoundary_countsOnlyThisMonthsPortion() throws Exception {
        Employee employee = employee(1L, new BigDecimal("44000.00"), null);
        when(employeeRepository.findAllWithUserByIdIn(List.of(1L))).thenReturn(List.of(employee));
        when(payrollRecordRepository.findEmployeeIdsAlreadyGenerated(LocalDate.of(2026, 3, 1), List.of(1L)))
                .thenReturn(List.of());
        // 10-day paid leave Feb 25 -> Mar 6; only Mar 1..6 (6 of 10 calendar days) count for March.
        when(leaveRequestRepository.findApprovedPaidLeaveOverlappingMonth(any(), any(), any()))
                .thenReturn(List.of(paidLeave(1L, LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 6), "10.0")));
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("payslips/EMP-001/2026-03.pdf");
        when(storageService.presignedGetUrl(anyString(), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        // present 16 + 6 paid = 22 payable -> full base.
        GeneratePayrollRequest request = new GeneratePayrollRequest(LocalDate.of(2026, 3, 1), 22,
                List.of(new PayrollLineRequest(1L, 16, null, null)));

        List<PayrollRecordResponse> responses = service().generate(request, "hr-uuid", 9L);

        assertThat(responses.get(0).lopDays()).isEqualByComparingTo("0.0");
        assertThat(responses.get(0).grossAmount()).isEqualByComparingTo("44000.00");
    }

    @Test
    void generate_hourlyEmployee_unaffectedByLeaveAndAbsence() throws Exception {
        Employee employee = employee(2L, null, new BigDecimal("500.00"));
        when(employeeRepository.findAllWithUserByIdIn(List.of(2L))).thenReturn(List.of(employee));
        when(payrollRecordRepository.findEmployeeIdsAlreadyGenerated(LocalDate.of(2026, 3, 1), List.of(2L)))
                .thenReturn(List.of());
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("payslips/EMP-002/2026-03.pdf");
        when(storageService.presignedGetUrl(anyString(), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        GeneratePayrollRequest request = new GeneratePayrollRequest(LocalDate.of(2026, 3, 1), 22,
                List.of(new PayrollLineRequest(2L, 20, new BigDecimal("40.00"), null)));

        List<PayrollRecordResponse> responses = service().generate(request, "hr-uuid", 9L);

        assertThat(responses.get(0).grossAmount()).isEqualByComparingTo("20000.00"); // 500 * 40
        assertThat(responses.get(0).lopDays()).isEqualByComparingTo("0.0");
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
