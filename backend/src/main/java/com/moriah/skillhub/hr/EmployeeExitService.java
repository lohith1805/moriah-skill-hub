package com.moriah.skillhub.hr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.hr.dto.EmployeeExitResponse;
import com.moriah.skillhub.hr.dto.InitiateEmployeeExitRequest;
import com.moriah.skillhub.hr.dto.UpdateEmployeeExitRequest;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.EmployeeExit;
import com.moriah.skillhub.hr.entity.EmployeeExitStatus;
import com.moriah.skillhub.hr.entity.EmployeeStatus;
import com.moriah.skillhub.hr.entity.ExitType;
import com.moriah.skillhub.hr.repository.EmployeeExitRepository;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.entity.UserStatus;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * HR Exit Management (gap B1.10, first slice). Initiating and driving an offboarding record;
 * {@link #complete} is the one transition with a side effect — it flips
 * {@code employees.status} ({@link EmployeeStatus#TERMINATED} for a {@link ExitType#TERMINATION},
 * {@link EmployeeStatus#EXITED} otherwise) and stamps {@code employees.date_of_exit}. HR_MANAGER/
 * ADMIN only (gated on {@link EmployeeExitController}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeExitService {

    private static final TypeReference<List<EmployeeExitResponse.ClearanceItem>> CHECKLIST_TYPE =
            new TypeReference<>() {
            };
    private static final Set<EmployeeExitStatus> OPEN_STATUSES =
            Set.of(EmployeeExitStatus.INITIATED, EmployeeExitStatus.IN_PROGRESS);

    private final EmployeeExitRepository exitRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public EmployeeExitResponse initiate(InitiateEmployeeExitRequest request, Long callerUserId) {
        Employee employee = requireEmployee(request.employeeId());
        if (exitRepository.existsByEmployeeIdAndStatusIn(employee.getId(), OPEN_STATUSES)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This employee already has an open exit record.");
        }

        EmployeeExit exit = new EmployeeExit();
        exit.setEmployee(employee);
        exit.setInitiatedBy(callerUserId);
        exit.setExitType(request.exitType());
        exit.setLastWorkingDay(request.lastWorkingDay());
        exit.setReason(blankToNull(request.reason()));
        exit.setNoticePeriodDays(request.noticePeriodDays());
        exit.setStatus(EmployeeExitStatus.INITIATED);
        exitRepository.save(exit);

        auditLogService.record(callerUserId, "EMPLOYEE_EXIT_INITIATED", "EmployeeExit", exit.getId(),
                null, employee.getEmployeeCode());
        log.info("[hr/exits] {} initiated {} exit for employee {}", callerUserId, exit.getExitType(), employee.getEmployeeCode());
        return toResponse(exit);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeExitResponse> list(EmployeeExitStatus status, Long employeeId, Pageable pageable) {
        return PageResponse.from(exitRepository.search(status, employeeId, pageable).map(this::toResponse));
    }

    @Transactional
    public EmployeeExitResponse update(Long id, UpdateEmployeeExitRequest request, Long callerUserId) {
        EmployeeExit exit = requireExit(id);
        if (exit.getStatus() == EmployeeExitStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This exit is already completed and can no longer be edited.");
        }

        exit.setExitType(request.exitType());
        exit.setLastWorkingDay(request.lastWorkingDay());
        exit.setReason(blankToNull(request.reason()));
        exit.setNoticePeriodDays(request.noticePeriodDays());
        exit.setStatus(request.status());
        exit.setClearanceChecklist(serializeChecklist(request.clearanceChecklist()));
        exit.setExitInterviewNotes(blankToNull(request.exitInterviewNotes()));

        auditLogService.record(callerUserId, "EMPLOYEE_EXIT_UPDATED", "EmployeeExit", exit.getId(), null, exit.getStatus());
        return toResponse(exit);
    }

    /**
     * The only transition that touches the {@code employees} row. Valid only from an open
     * status; a second complete is a {@code BUSINESS_RULE_VIOLATION}.
     */
    @Transactional
    public EmployeeExitResponse complete(Long id, Long callerUserId) {
        EmployeeExit exit = requireExit(id);
        if (!OPEN_STATUSES.contains(exit.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only an INITIATED or IN_PROGRESS exit can be completed.");
        }

        exit.setStatus(EmployeeExitStatus.COMPLETED);
        exit.setCompletedAt(Instant.now());

        Employee employee = exit.getEmployee();
        employee.setStatus(exit.getExitType() == ExitType.TERMINATION
                ? EmployeeStatus.TERMINATED : EmployeeStatus.EXITED);
        employee.setDateOfExit(exit.getLastWorkingDay());

        // Finalising an exit has to actually revoke access, not just update the HR record.
        // Mirror AdminUserService.updateStatus: flip the login account to TERMINATED and bump
        // token_version so any live session is rejected on its next request. Both a clean EXITED
        // and a for-cause TERMINATED departure end login access — the EXITED/TERMINATED
        // distinction on the employees row is what HrLetterService reads for letter eligibility.
        User user = employee.getUser();
        UserStatus previousUserStatus = user.getStatus();
        if (previousUserStatus != UserStatus.TERMINATED) {
            user.setStatus(UserStatus.TERMINATED);
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
            auditLogService.record(callerUserId, "USER_STATUS_CHANGED", "User", user.getId(),
                    previousUserStatus, UserStatus.TERMINATED);
        }

        auditLogService.record(callerUserId, "EMPLOYEE_EXIT_COMPLETED", "EmployeeExit", exit.getId(),
                null, employee.getStatus());
        log.info("[hr/exits] {} completed exit {} — employee {} is now {}, login account terminated",
                callerUserId, exit.getId(), employee.getEmployeeCode(), employee.getStatus());
        return toResponse(exit);
    }

    private Employee requireEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, id));
    }

    private EmployeeExit requireExit(Long id) {
        return exitRepository.findWithEmployeeById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_EXIT_NOT_FOUND, id));
    }

    private String serializeChecklist(List<UpdateEmployeeExitRequest.ClearanceItemInput> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(items.stream()
                    .map(i -> new EmployeeExitResponse.ClearanceItem(i.label(), i.done()))
                    .toList());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize clearance checklist", e);
        }
    }

    private List<EmployeeExitResponse.ClearanceItem> parseChecklist(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, CHECKLIST_TYPE);
        } catch (Exception e) {
            log.warn("[hr/exits] unparseable clearance checklist on exit record, treating as empty");
            return List.of();
        }
    }

    private EmployeeExitResponse toResponse(EmployeeExit x) {
        Employee e = x.getEmployee();
        return new EmployeeExitResponse(
                x.getId(),
                e.getId(),
                e.getEmployeeCode(),
                e.getUser().getFullName(),
                x.getExitType(),
                x.getLastWorkingDay(),
                x.getReason(),
                x.getNoticePeriodDays(),
                x.getStatus(),
                parseChecklist(x.getClearanceChecklist()),
                x.getExitInterviewNotes(),
                x.getCompletedAt(),
                x.getCreatedAt(),
                x.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
