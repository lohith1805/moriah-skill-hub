package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.hr.dto.CreateLeaveRequest;
import com.moriah.skillhub.hr.dto.LeaveDecisionRequest;
import com.moriah.skillhub.hr.dto.LeaveRequestResponse;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.LeaveRequest;
import com.moriah.skillhub.hr.entity.LeaveStatus;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.hr.repository.LeaveRequestRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * build-plan.md feature 19: "Leave routed to reporting_manager_id; overlapping approved leave
 * returns 409." Routing is informational (the reporting manager is who is <i>expected</i> to
 * decide) — {@link #decide} itself authorizes either that specific manager or an HR override
 * (HR_MANAGER/ADMIN), matching how every other ownership-shaped check in this codebase is
 * enforced in the service, not solely via {@code @PreAuthorize}. The overlap check runs at
 * decision time, on approval only — a student may freely submit overlapping requests, the
 * conflict is between two <i>approved</i> leaves, per the literal build-plan.md wording.
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public LeaveRequestResponse create(CreateLeaveRequest request, Long callerUserId) {
        if (!employeeRepository.existsByUserId(callerUserId)) {
            throw new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, callerUserId);
        }

        LeaveRequest leave = new LeaveRequest();
        leave.setUser(userRepository.getReferenceById(callerUserId));
        leave.setLeaveType(request.leaveType());
        leave.setFromDate(request.fromDate());
        leave.setToDate(request.toDate());
        leave.setDays(BigDecimal.valueOf(ChronoUnit.DAYS.between(request.fromDate(), request.toDate()) + 1));
        leave.setReason(request.reason());
        leave.setStatus(LeaveStatus.PENDING);
        leaveRequestRepository.save(leave);

        return toResponse(leave);
    }

    @Transactional
    public LeaveRequestResponse decide(Long leaveId, LeaveDecisionRequest request, Long callerUserId) {
        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LEAVE_REQUEST_NOT_FOUND, leaveId));

        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException(ErrorCode.LEAVE_REQUEST_ALREADY_DECIDED);
        }
        requireReportingManagerOrHr(leave.getUser().getId(), callerUserId);

        if (request.decision() == LeaveStatus.APPROVED
                && leaveRequestRepository.existsOverlappingApproved(
                        leave.getUser().getId(), leave.getFromDate(), leave.getToDate(), leave.getId())) {
            throw new BusinessException(ErrorCode.LEAVE_OVERLAPS_APPROVED_LEAVE);
        }

        LeaveStatus previousStatus = leave.getStatus();
        leave.setStatus(request.decision());
        leave.setApprovedBy(userRepository.getReferenceById(callerUserId));
        leave.setDecidedAt(Instant.now());
        leaveRequestRepository.save(leave);
        auditLogService.record(callerUserId, "LEAVE_DECIDED", "LeaveRequest", leave.getId(),
                previousStatus, leave.getStatus());

        return toResponse(leave);
    }

    /** No role, including HR/ADMIN, may decide a request whose requester is the caller themself —
     * checked before the HR-override short-circuit below so an HR_MANAGER with their own {@code
     * employees} row can't self-approve their own leave. */
    private void requireReportingManagerOrHr(Long requesterUserId, Long callerUserId) {
        if (callerUserId.equals(requesterUserId)) {
            throw new ForbiddenOperationException(ErrorCode.SELF_DECISION_NOT_ALLOWED);
        }

        boolean callerIsHr = SecurityUtils.currentUserRoles().contains(RoleCode.HR_MANAGER.name())
                || SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name());
        if (callerIsHr) {
            return;
        }

        Employee requesterEmployee = employeeRepository.findByUserId(requesterUserId).orElse(null);
        Long reportingManagerUserId = requesterEmployee == null || requesterEmployee.getReportingManager() == null
                ? null : requesterEmployee.getReportingManager().getUser().getId();
        if (!callerUserId.equals(reportingManagerUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_REPORTING_MANAGER);
        }
    }

    private LeaveRequestResponse toResponse(LeaveRequest leave) {
        User user = leave.getUser();
        return new LeaveRequestResponse(
                leave.getId(),
                user.getUuid(),
                leave.getLeaveType(),
                leave.getFromDate(),
                leave.getToDate(),
                leave.getDays(),
                leave.getReason(),
                leave.getStatus(),
                leave.getApprovedBy() == null ? null : leave.getApprovedBy().getUuid(),
                leave.getDecidedAt());
    }
}
