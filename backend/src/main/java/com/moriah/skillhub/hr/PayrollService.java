package com.moriah.skillhub.hr;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
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
import java.util.Base64;
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
 * A salaried employee is paid per working day for the days they were <b>present or on approved
 * paid leave</b> ({@code SICK}/{@code CASUAL}/{@code EARNED}); every other working day is loss
 * of pay (unpaid leave and plain absence alike):
 * <pre>gross = baseSalary * (presentDays + paidLeaveDays) / workingDays</pre>
 * {@code lopDays = workingDays - presentDays - paidLeaveDays} (floored at 0) is recorded for the
 * payslip. Holidays are assumed already excluded from the HR-entered {@code workingDays}. Hourly
 * employees are unaffected — their {@code sessionHours} already reflect worked time. {@code
 * deductions} remains a separate, HR-entered amount applied after gross.
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
    private final NotificationService notificationService;

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
        Map<Long, BigDecimal> paidLeaveDaysByEmployeeId =
                paidLeaveDaysByEmployeeId(employeesById.values(), request.periodMonth());

        return request.lines().stream()
                .map(line -> generateOne(request, line, employeesById, alreadyGenerated,
                        paidLeaveDaysByEmployeeId, callerUuid, callerUserId))
                .toList();
    }

    private PayrollRecordResponse generateOne(GeneratePayrollRequest request, PayrollLineRequest line,
            Map<Long, Employee> employeesById, Set<Long> alreadyGenerated,
            Map<Long, BigDecimal> paidLeaveDaysByEmployeeId, String callerUuid, Long callerUserId) {
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
        boolean hourly = employee.getHourlyRate() != null;
        // Approved paid leave (SICK/CASUAL/EARNED) is paid alongside present days; everything else
        // in the month is loss of pay. Hourly: 0 — session hours already reflect worked time.
        BigDecimal paidLeaveDays = hourly ? BigDecimal.ZERO
                : paidLeaveDaysByEmployeeId.getOrDefault(employee.getId(), BigDecimal.ZERO);
        BigDecimal lopDays = hourly ? BigDecimal.ZERO
                : BigDecimal.valueOf(request.workingDays())
                        .subtract(BigDecimal.valueOf(line.presentDays()))
                        .subtract(paidLeaveDays)
                        .max(BigDecimal.ZERO);
        BigDecimal grossAmount = computeGrossAmount(employee, line, request.workingDays(), paidLeaveDays)
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
        record.setLopDays(lopDays);
        record.setSessionHours(hourly ? line.sessionHours() : null);
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

        notifyPayslipReady(employee, record, pdfBytes);
        return toResponse(record, callerUuid);
    }

    /** Tells the employee their payslip is ready: an in-app notification, plus an email with the
     * PDF attached (the email only actually leaves once SendGrid is configured — see {@code
     * EmailDispatcher}). Uses {@code enqueueNow}, not {@code enqueueAfterCommit}: the payslip row
     * is already committed by the time this runs ({@link #generateOne} deliberately holds no
     * ambient transaction — feature 23 hardening). A notification failure never breaks payroll. */
    private void notifyPayslipReady(Employee employee, PayrollRecord record, byte[] payslipPdf) {
        Long userId = employee.getUser().getId();
        String period = PERIOD_KEY_FORMAT.format(record.getPeriodMonth());
        try {
            notificationService.enqueueNow(userId, NotificationChannel.IN_APP, "PAYSLIP_READY", Map.of(
                    "periodMonth", period,
                    "netAmount", record.getNetAmount().toPlainString(),
                    "employeeCode", employee.getEmployeeCode()));
        } catch (RuntimeException e) {
            log.warn("[hr/payroll] in-app payslip notification failed for {} {}: {}",
                    employee.getEmployeeCode(), period, e.getMessage());
        }
        String email = employee.getUser().getEmail();
        if (email != null && !email.isBlank()) {
            try {
                notificationService.enqueueNow(userId, NotificationChannel.EMAIL, "PAYSLIP_READY", Map.of(
                        "to", email,
                        "subject", "Your payslip for " + period,
                        "body", "Hi " + employee.getUser().getFullName() + ",\n\n"
                                + "Your payslip for " + period + " is attached. Net pay: "
                                + record.getNetAmount().toPlainString() + ".\n\n"
                                + "You can also download it any time from My Payslips in the portal.\n\n"
                                + "— Moriah Skill Hub HR",
                        "attachmentBase64", Base64.getEncoder().encodeToString(payslipPdf),
                        "attachmentFilename", employee.getEmployeeCode() + "-" + period + ".pdf",
                        "attachmentContentType", "application/pdf"));
            } catch (RuntimeException e) {
                log.warn("[hr/payroll] email payslip notification failed for {} {}: {}",
                        employee.getEmployeeCode(), period, e.getMessage());
            }
        }
    }

    /** {@code GET /api/v1/hr/payroll/me} — the caller's own payslips, newest first. Any
     * authenticated user; a caller with no {@code employees} row gets an empty page. Presigns
     * each payslip as the caller ({@code OwnershipGuard#canAccessPayslip} grants an employee
     * their own {@code payslips/{code}/...} key). */
    @Transactional(readOnly = true)
    public PageResponse<PayrollRecordResponse> listMine(Long callerUserId, String callerUuid, Pageable pageable) {
        return PageResponse.from(payrollRecordRepository.findByEmployeeUserIdOrderByPeriodMonthDesc(callerUserId, pageable)
                .map(record -> toResponse(record, callerUuid)));
    }

    private BigDecimal computeGrossAmount(Employee employee, PayrollLineRequest line, int workingDays,
            BigDecimal paidLeaveDays) {
        if (employee.getHourlyRate() != null) {
            if (line.sessionHours() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "sessionHours is required for employee " + employee.getEmployeeCode() + " (hourly-rate compensation).");
            }
            return employee.getHourlyRate().multiply(line.sessionHours());
        }
        // gross = base * (present + paid leave) / workingDays. workingDays is @Min(1) on the
        // request so the division is always safe; payable is capped so a mis-entered
        // present + leave total can never pay more than the whole month.
        BigDecimal payableDays = BigDecimal.valueOf(line.presentDays())
                .add(paidLeaveDays == null ? BigDecimal.ZERO : paidLeaveDays)
                .min(BigDecimal.valueOf(workingDays))
                .max(BigDecimal.ZERO);
        return employee.getBaseSalary()
                .multiply(payableDays)
                .divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP);
    }

    /** Approved <b>paid</b> leave days ({@code SICK}/{@code CASUAL}/{@code EARNED}) per employee
     * that fall inside {@code periodMonth} — these are paid alongside present days. One flat query
     * for the whole batch; keyed by employee id (the {@code leave_requests} table is keyed by
     * {@code user_id}, so the mapping is resolved here). */
    private Map<Long, BigDecimal> paidLeaveDaysByEmployeeId(Collection<Employee> employees, LocalDate periodMonth) {
        LocalDate monthStart = periodMonth.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        Map<Long, Long> employeeIdByUserId = employees.stream()
                .collect(Collectors.toMap(e -> e.getUser().getId(), Employee::getId, (a, b) -> a));

        Map<Long, BigDecimal> byEmployeeId = new HashMap<>();
        for (LeaveRequest leave : leaveRequestRepository.findApprovedPaidLeaveOverlappingMonth(
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
            if (record.getLopDays() != null && record.getLopDays().signum() > 0) {
                doc.add(new Paragraph("Loss of pay: " + record.getLopDays()
                        + " working day(s) not present / not on paid leave — base salary prorated"));
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
                record.getLopDays(),
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
