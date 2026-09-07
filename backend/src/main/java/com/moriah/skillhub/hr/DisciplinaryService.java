package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.hr.dto.CreateDisciplinaryActionRequest;
import com.moriah.skillhub.hr.dto.DisciplinaryActionResponse;
import com.moriah.skillhub.hr.dto.UpdateDisciplinaryActionRequest;
import com.moriah.skillhub.hr.entity.DisciplinaryAction;
import com.moriah.skillhub.hr.entity.DisciplinarySeverity;
import com.moriah.skillhub.hr.entity.DisciplinaryStatus;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.repository.DisciplinaryActionRepository;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * HR Disciplinary (gap B1.10). CRUD for {@code disciplinary_actions}, HR_MANAGER/ADMIN. Never
 * touches the {@code employees} row — a {@code TERMINATION_RECOMMENDATION} is a recommendation,
 * the actual termination goes through the exit flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisciplinaryService {

    private final DisciplinaryActionRepository actionRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public DisciplinaryActionResponse create(CreateDisciplinaryActionRequest request, Long callerUserId) {
        Employee employee = requireEmployee(request.employeeId());

        DisciplinaryAction action = new DisciplinaryAction();
        action.setEmployee(employee);
        action.setRaisedBy(callerUserId);
        action.setActionType(request.actionType());
        action.setSeverity(request.severity());
        action.setIncidentDate(request.incidentDate());
        action.setDescription(request.description());
        action.setStatus(DisciplinaryStatus.OPEN);
        actionRepository.save(action);

        auditLogService.record(callerUserId, "DISCIPLINARY_ACTION_RAISED", "DisciplinaryAction", action.getId(),
                null, request.severity());
        log.info("[hr/disciplinary] {} raised {} ({}) against employee {}",
                callerUserId, action.getActionType(), action.getSeverity(), employee.getEmployeeCode());
        return toResponse(action);
    }

    @Transactional(readOnly = true)
    public PageResponse<DisciplinaryActionResponse> list(DisciplinaryStatus status, DisciplinarySeverity severity,
            Long employeeId, Pageable pageable) {
        return PageResponse.from(actionRepository.search(status, severity, employeeId, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public DisciplinaryActionResponse get(Long id) {
        return toResponse(requireAction(id));
    }

    @Transactional
    public DisciplinaryActionResponse update(Long id, UpdateDisciplinaryActionRequest request, Long callerUserId) {
        DisciplinaryAction action = requireAction(id);
        DisciplinaryStatus previous = action.getStatus();

        action.setActionType(request.actionType());
        action.setSeverity(request.severity());
        action.setIncidentDate(request.incidentDate());
        action.setDescription(request.description());
        action.setActionTaken(blankToNull(request.actionTaken()));
        action.setStatus(request.status());
        action.setResolutionNotes(blankToNull(request.resolutionNotes()));

        if (request.status() == DisciplinaryStatus.ACKNOWLEDGED && action.getAcknowledgedAt() == null) {
            action.setAcknowledgedAt(Instant.now());
        }
        if (request.status() == DisciplinaryStatus.RESOLVED && action.getResolvedAt() == null) {
            action.setResolvedAt(Instant.now());
        }

        auditLogService.record(callerUserId, "DISCIPLINARY_ACTION_UPDATED", "DisciplinaryAction", action.getId(),
                previous, action.getStatus());
        return toResponse(action);
    }

    private Employee requireEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, id));
    }

    private DisciplinaryAction requireAction(Long id) {
        return actionRepository.findWithEmployeeById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DISCIPLINARY_ACTION_NOT_FOUND, id));
    }

    private DisciplinaryActionResponse toResponse(DisciplinaryAction d) {
        Employee e = d.getEmployee();
        return new DisciplinaryActionResponse(
                d.getId(),
                e.getId(),
                e.getEmployeeCode(),
                e.getUser().getFullName(),
                d.getActionType(),
                d.getSeverity(),
                d.getIncidentDate(),
                d.getDescription(),
                d.getActionTaken(),
                d.getStatus(),
                d.getAcknowledgedAt(),
                d.getResolvedAt(),
                d.getResolutionNotes(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
