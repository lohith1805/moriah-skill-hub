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
import com.moriah.skillhub.hr.entity.LeaveRequest;
import com.moriah.skillhub.hr.entity.PayrollRecord;
import com.moriah.skillhub.hr.entity.PayrollStatus;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.hr.repository.LeaveRequestRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
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
 * <p>
 * A salaried employee's gross is the base salary <b>less loss-of-pay for approved {@code UNPAID}
 * leave days</b> that fall inside the period ({@code base / workingDays} per day) — approved
 * paid leave (SICK/CASUAL/EARNED) still pays in full. {@code presentDays} stays informational
 * (shown on the payslip, never a multiplier). Hourly employees are unaffected — their {@code
 * sessionHours} already reflect actual worked time. {@code deductions} remains a separate,
 * HR-entered amount applied after gross.
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
    private final LeaveRequestRepository leaveRequestRepository;

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
        Map<Long, BigDecimal> unpaidLeaveDaysByEmployeeId =
                unpaidLeaveDaysByEmployeeId(employeesById.values(), request.periodMonth());

        return request.lines().stream()
                .map(line -> generateOne(request, line, employeesById, alreadyGenerated,
                        unpaidLeaveDaysByEmployeeId, callerUuid, callerUserId))
                .toList();
    }

    private PayrollRecordResponse generateOne(GeneratePayrollRequest request, PayrollLineRequest line,
            Map<Long, Employee> employeesById, Set<Long> alreadyGenerated,
            Map<Long, BigDecimal> unpaidLeaveDaysByEmployeeId, String callerUuid, Long callerUserId) {
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
        // Salaried: base salary less loss-of-pay for approved UNPAID leave days in this period.
        // Hourly: 0 — session hours already reflect actual worked time.
        BigDecimal unpaidLeaveDays = employee.getHourlyRate() != null ? BigDecimal.ZERO
                : unpaidLeaveDaysByEmployeeId.getOrDefault(employee.getId(), BigDecimal.ZERO);
        BigDecimal grossAmount = computeGrossAmount(employee, line, request.workingDays(), unpaidLeaveDays)
                .setScale(2, RoundingMode.HALF_UP);
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
        record.setUnpaidLeaveDays(unpaidLeaveDays);
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

    private BigDecimal computeGrossAmount(Employee employee, PayrollLineRequest line, int workingDays,
            BigDecimal unpaidLeaveDays) {
        if (employee.getHourlyRate() != null) {
            if (line.sessionHours() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "sessionHours is required for employee " + employee.getEmployeeCode() + " (hourly-rate compensation).");
            }
            return employee.getHourlyRate().multiply(line.sessionHours());
        }
        BigDecimal base = employee.getBaseSalary();
        if (unpaidLeaveDays == null || unpaidLeaveDays.signum() <= 0) {
            return base;
        }
        // Loss of pay: (base / workingDays) per unpaid day, the whole month's base at most.
        // workingDays is @Min(1) on the request, so the division is always safe.
        BigDecimal cappedDays = unpaidLeaveDays.min(BigDecimal.valueOf(workingDays));
        BigDecimal lossOfPay = base.multiply(cappedDays)
                .divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP);
        return base.subtract(lossOfPay).max(BigDecimal.ZERO);
    }

    /** Approved {@code UNPAID} leave days per employee that fall inside {@code periodMonth}. One
     * flat query for the whole batch; the result is keyed by employee id (the {@code
     * leave_requests} table is keyed by {@code user_id}, so the mapping is resolved here). */
    private Map<Long, BigDecimal> unpaidLeaveDaysByEmployeeId(Collection<Employee> employees, LocalDate periodMonth) {
        LocalDate monthStart = periodMonth.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        Map<Long, Long> employeeIdByUserId = employees.stream()
                .collect(Collectors.toMap(e -> e.getUser().getId(), Employee::getId, (a, b) -> a));

        Map<Long, BigDecimal> byEmployeeId = new HashMap<>();
        for (LeaveRequest leave : leaveRequestRepository.findApprovedUnpaidOverlappingMonth(
                employeeIdByUserId.keySet(), monthStart, monthEnd)) {
            Long employeeId = employeeIdByUserId.get(leave.getUser().getId());
            if (employeeId != null) {
                byEmployeeId.merge(employeeId, daysWithinMonth(leave, monthStart, monthEnd), BigDecimal::add);
            }
        }
        return byEmployeeId;
    }

    /** The portion of a leave's recorded {@code days} that falls inside {@code [monthStart,
     * monthEnd]}. A leave entirely within the month (the common case) contributes its {@code days}
     * unchanged; one that straddles a month boundary is split by its calendar-day overlap
     * fraction. */
    private BigDecimal daysWithinMonth(LeaveRequest leave, LocalDate monthStart, LocalDate monthEnd) {
        LocalDate from = leave.getFromDate();
        LocalDate to = leave.getToDate();
        if (!from.isBefore(monthStart) && !to.isAfter(monthEnd)) {
            return leave.getDays();
        }
        LocalDate overlapFrom = from.isBefore(monthStart) ? monthStart : from;
        LocalDate overlapTo = to.isAfter(monthEnd) ? monthEnd : to;
        long overlapDays = ChronoUnit.DAYS.between(overlapFrom, overlapTo) + 1;
        long spanDays = ChronoUnit.DAYS.between(from, to) + 1;
        return leave.getDays()
                .multiply(BigDecimal.valueOf(overlapDays))
                .divide(BigDecimal.valueOf(spanDays), 1, RoundingMode.HALF_UP);
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
            if (record.getUnpaidLeaveDays() != null && record.getUnpaidLeaveDays().signum() > 0) {
                doc.add(new Paragraph("Unpaid leave: " + record.getUnpaidLeaveDays()
                        + " day(s) — base salary prorated (loss of pay)"));
            }
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
                record.getUnpaidLeaveDays(),
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
