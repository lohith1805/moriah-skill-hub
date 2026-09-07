package com.moriah.skillhub.hr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.hr.dto.CreateOnboardingRequest;
import com.moriah.skillhub.hr.dto.EmployeeOnboardingResponse;
import com.moriah.skillhub.hr.dto.UpdateOnboardingRequest;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.EmployeeOnboarding;
import com.moriah.skillhub.hr.entity.OnboardingStatus;
import com.moriah.skillhub.hr.repository.EmployeeOnboardingRepository;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
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
 * HR Onboarding (gap B1.10). CRUD for {@code employee_onboardings}, HR_MANAGER/ADMIN. Completing
 * an onboarding stamps {@code completedAt} but — unlike an exit — never touches the
 * {@code employees} row, so there is no dedicated {@code /complete} endpoint; {@code PUT} sets
 * the status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {

    private static final TypeReference<List<EmployeeOnboardingResponse.ChecklistItem>> CHECKLIST_TYPE =
            new TypeReference<>() {
            };
    private static final Set<OnboardingStatus> OPEN_STATUSES =
            Set.of(OnboardingStatus.NOT_STARTED, OnboardingStatus.IN_PROGRESS);

    private final EmployeeOnboardingRepository onboardingRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public EmployeeOnboardingResponse create(CreateOnboardingRequest request, Long callerUserId) {
        Employee employee = requireEmployee(request.employeeId());
        if (onboardingRepository.existsByEmployeeIdAndStatusIn(employee.getId(), OPEN_STATUSES)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This employee already has an open onboarding record.");
        }

        EmployeeOnboarding onboarding = new EmployeeOnboarding();
        onboarding.setEmployee(employee);
        onboarding.setInitiatedBy(callerUserId);
        onboarding.setBuddyId(resolveBuddyId(request.buddyUuid()));
        onboarding.setStartDate(request.startDate());
        onboarding.setStatus(OnboardingStatus.NOT_STARTED);
        onboardingRepository.save(onboarding);

        auditLogService.record(callerUserId, "ONBOARDING_CREATED", "EmployeeOnboarding", onboarding.getId(),
                null, employee.getEmployeeCode());
        return toResponse(onboarding);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeOnboardingResponse> list(OnboardingStatus status, Long employeeId, Pageable pageable) {
        return PageResponse.from(onboardingRepository.search(status, employeeId, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public EmployeeOnboardingResponse get(Long id) {
        return toResponse(requireOnboarding(id));
    }

    @Transactional
    public EmployeeOnboardingResponse update(Long id, UpdateOnboardingRequest request, Long callerUserId) {
        EmployeeOnboarding onboarding = requireOnboarding(id);

        onboarding.setStartDate(request.startDate());
        onboarding.setBuddyId(resolveBuddyId(request.buddyUuid()));
        boolean nowCompleted = request.status() == OnboardingStatus.COMPLETED
                && onboarding.getStatus() != OnboardingStatus.COMPLETED;
        onboarding.setStatus(request.status());
        if (nowCompleted) {
            onboarding.setCompletedAt(Instant.now());
        }
        onboarding.setChecklist(serializeChecklist(request.checklist()));
        onboarding.setNotes(blankToNull(request.notes()));

        auditLogService.record(callerUserId, "ONBOARDING_UPDATED", "EmployeeOnboarding", onboarding.getId(),
                null, onboarding.getStatus());
        return toResponse(onboarding);
    }

    private Long resolveBuddyId(String buddyUuid) {
        if (buddyUuid == null || buddyUuid.isBlank()) {
            return null;
        }
        return userRepository.findByUuid(buddyUuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, buddyUuid))
                .getId();
    }

    private Employee requireEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, id));
    }

    private EmployeeOnboarding requireOnboarding(Long id) {
        return onboardingRepository.findWithEmployeeById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMPLOYEE_ONBOARDING_NOT_FOUND, id));
    }

    private String serializeChecklist(List<UpdateOnboardingRequest.ChecklistItemInput> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(items.stream()
                    .map(i -> new EmployeeOnboardingResponse.ChecklistItem(i.label(), i.done()))
                    .toList());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize onboarding checklist", e);
        }
    }

    private List<EmployeeOnboardingResponse.ChecklistItem> parseChecklist(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, CHECKLIST_TYPE);
        } catch (Exception e) {
            log.warn("[hr/onboardings] unparseable checklist, treating as empty");
            return List.of();
        }
    }

    private EmployeeOnboardingResponse toResponse(EmployeeOnboarding o) {
        Employee e = o.getEmployee();
        return new EmployeeOnboardingResponse(
                o.getId(),
                e.getId(),
                e.getEmployeeCode(),
                e.getUser().getFullName(),
                e.getUser().getUuid(),
                o.getBuddyId(),
                o.getStartDate(),
                o.getStatus(),
                parseChecklist(o.getChecklist()),
                o.getNotes(),
                o.getCompletedAt(),
                o.getCreatedAt(),
                o.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
