package com.moriah.skillhub.hr;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.hr.dto.GeneratePayrollRequest;
import com.moriah.skillhub.hr.dto.PayrollLineRequest;
import com.moriah.skillhub.hr.dto.PayrollRecordResponse;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.PayrollRecord;
import com.moriah.skillhub.hr.entity.PayrollStatus;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.hr.repository.PayrollRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * build-plan.md feature 19: "Payroll: base salary, or hourly_rate x session_hours for trainers,
 * minus deductions... Unique (employee_id, period_month) — a month cannot be generated twice."
 * One call computes every line's numbers, renders its payslip PDF, and uploads it — {@code
 * payroll_records.status} goes straight to {@code FINALISED}, not {@code DRAFT} (see {@link
 * PayrollStatus}'s own Javadoc for why {@code DRAFT}/{@code PAID} exist in schema without a route
 * to reach them from this feature).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollService {

    private static final String KEY_PREFIX = "payslips/";
    private static final DateTimeFormatter PERIOD_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PayrollRecordRepository payrollRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    /** Feature 23 hardening: deliberately <b>not</b> {@code @Transactional} any more — {@link
     * #generateOne} calls {@code storageService.uploadTrusted} (an outbound S3 call) once per
     * line, and this method previously wrapped the <i>entire batch's</i> loop, including every
     * one of those calls, in one open transaction (AGENTS.md: "never make an outbound HTTP call
     * inside a transaction" — a batch of N employees held one DB transaction/connection open
     * across N sequential HTTP round-trips). {@code generateOne} is a private method called via
     * plain {@code this.generateOne(...)} self-invocation, so it was never separately proxied
     * anyway — each of its repository calls (the lookups in {@link #generate} itself, and {@code
     * payrollRecordRepository.save(record)} inside {@code generateOne}) already runs in its own
     * short transaction via Spring Data's per-call proxy default, same precedent {@code
     * SubmissionVerificationRetryJob.retry()}'s own Javadoc documents. One employee's payslip
     * failing no longer rolls back an already-persisted-and-uploaded sibling line's record either
     * — an improvement, not a regression: a partial batch failure previously took down every
     * already-processed line with it.
     */
    public List<PayrollRecordResponse> generate(GeneratePayrollRequest request, String callerUuid, Long callerUserId) {
        List<Long> employeeIds = request.lines().stream().map(PayrollLineRequest::employeeId).toList();
        Map<Long, Employee> employeesById = employeeRepository.findAllWithUserByIdIn(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Set<Long> alreadyGenerated = new HashSet<>(
                payrollRecordRepository.findEmployeeIdsAlreadyGenerated(request.periodMonth(), employeeIds));

        return request.lines().stream()
                .map(line -> generateOne(request, line, employeesById, alreadyGenerated, callerUuid, callerUserId))
                .toList();
    }

    private PayrollRecordResponse generateOne(GeneratePayrollRequest request, PayrollLineRequest line,
            Map<Long, Employee> employeesById, Set<Long> alreadyGenerated, String callerUuid, Long callerUserId) {
        Employee employee = employeesById.get(line.employeeId());
        if (employee == null) {
            throw new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, line.employeeId());
        }

        // Same-request duplicate employeeIds are already rejected by GeneratePayrollRequest's
        // compact constructor — this only guards a genuine "already generated in an earlier call"
        // regenerate attempt, the actual build-plan.md "cannot be generated twice" rule.
        if (alreadyGenerated.contains(employee.getId())) {
            throw new BusinessException(ErrorCode.PAYROLL_ALREADY_GENERATED);
        }
        if (line.presentDays() > request.workingDays()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "presentDays cannot exceed workingDays for employee " + employee.getEmployeeCode() + ".");
        }

        BigDecimal deductions = (line.deductions() == null ? BigDecimal.ZERO : line.deductions())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal grossAmount = computeGrossAmount(employee, line).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(deductions);
        if (netAmount.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "deductions cannot exceed the gross amount for employee " + employee.getEmployeeCode() + ".");
        }

        PayrollRecord record = new PayrollRecord();
        record.setEmployee(employee);
        record.setPeriodMonth(request.periodMonth());
        record.setWorkingDays(request.workingDays());
        record.setPresentDays(line.presentDays());
        record.setSessionHours(employee.getHourlyRate() != null ? line.sessionHours() : null);
        record.setGrossAmount(grossAmount);
        record.setDeductions(deductions);
        record.setNetAmount(netAmount);
        record.setStatus(PayrollStatus.FINALISED);

        byte[] pdfBytes = renderPayslip(employee, record);
        String key = KEY_PREFIX + employee.getEmployeeCode() + "/" + PERIOD_KEY_FORMAT.format(request.periodMonth()) + ".pdf";
        record.setPayslipKey(storageService.uploadTrusted(key, pdfBytes, "application/pdf"));

        payrollRecordRepository.save(record);
        auditLogService.record(callerUserId, "PAYROLL_GENERATED", "PayrollRecord", record.getId(), null, record.getNetAmount());
        log.info("[hr/payroll] generated payroll for employee {} period {}", employee.getEmployeeCode(), request.periodMonth());

        return toResponse(record, callerUuid);
    }

    private BigDecimal computeGrossAmount(Employee employee, PayrollLineRequest line) {
        if (employee.getHourlyRate() != null) {
            if (line.sessionHours() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "sessionHours is required for employee " + employee.getEmployeeCode() + " (hourly-rate compensation).");
            }
            return employee.getHourlyRate().multiply(line.sessionHours());
        }
        return employee.getBaseSalary();
    }

    private byte[] renderPayslip(Employee employee, PayrollRecord record) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Moriah Skill Hub — Payslip"));
            doc.add(new Paragraph("Employee: " + employee.getUser().getFullName() + " (" + employee.getEmployeeCode() + ")"));
            doc.add(new Paragraph("Period: " + record.getPeriodMonth()));
            doc.add(new Paragraph("Working days: " + record.getWorkingDays() + ", Present days: " + record.getPresentDays()));
            if (record.getSessionHours() != null) {
                doc.add(new Paragraph("Session hours: " + record.getSessionHours()));
            }
            doc.add(new Paragraph("Gross amount: " + record.getGrossAmount()));
            doc.add(new Paragraph("Deductions: " + record.getDeductions()));
            doc.add(new Paragraph("Net amount: " + record.getNetAmount()));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[hr/payroll] payslip PDF rendering failed for employee {}", employee.getEmployeeCode(), e);
            throw new IllegalStateException("Payslip PDF rendering failed", e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PayrollRecordResponse> list(LocalDate month, String callerUuid, Pageable pageable) {
        Page<PayrollRecord> page = payrollRecordRepository.findByPeriodMonth(month.withDayOfMonth(1), pageable);
        return PageResponse.from(page.map(record -> toResponse(record, callerUuid)));
    }

    private PayrollRecordResponse toResponse(PayrollRecord record, String callerUuid) {
        Employee employee = record.getEmployee();
        String downloadUrl = record.getPayslipKey() == null ? null : presign(record.getPayslipKey(), callerUuid);
        return new PayrollRecordResponse(
                record.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getUser().getFullName(),
                record.getPeriodMonth(),
                record.getWorkingDays(),
                record.getPresentDays(),
                record.getSessionHours(),
                record.getGrossAmount(),
                record.getDeductions(),
                record.getNetAmount(),
                downloadUrl,
                record.getStatus());
    }

    /** {@code OwnershipGuard} now recognizes {@code payslips/{employeeCode}/...} — an HR_MANAGER/
     * ADMIN caller (the only roles {@code GET /hr/payroll?month=} allows) is granted access to
     * every payslip; see {@code OwnershipGuard#canAccessPayslip}. */
    private String presign(String key, String callerUuid) {
        URL url = storageService.presignedGetUrl(callerUuid, key, Duration.ofMinutes(Constants.PRESIGNED_URL_TTL_MINUTES));
        return url.toString();
    }
}
