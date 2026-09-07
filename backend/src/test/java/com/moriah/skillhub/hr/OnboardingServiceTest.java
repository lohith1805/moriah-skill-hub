package com.moriah.skillhub.hr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.audit.AuditLogService;
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
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private EmployeeOnboardingRepository onboardingRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OnboardingService service;

    private Employee employee(long id) {
        User u = new User();
        u.setId(90L);
        u.setFullName("New Hire");
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeCode("EMP-050");
        e.setUser(u);
        return e;
    }

    private EmployeeOnboarding onboarding(long id, OnboardingStatus status) {
        EmployeeOnboarding o = new EmployeeOnboarding();
        o.setId(id);
        o.setEmployee(employee(5L));
        o.setStartDate(LocalDate.of(2026, 9, 1));
        o.setStatus(status);
        return o;
    }

    @Test
    void create_startsNotStartedAndResolvesBuddy() {
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee(5L)));
        when(onboardingRepository.existsByEmployeeIdAndStatusIn(eq(5L), anyCollection())).thenReturn(false);
        User buddy = new User();
        buddy.setId(77L);
        buddy.setUuid("buddy-uuid");
        when(userRepository.findByUuid("buddy-uuid")).thenReturn(Optional.of(buddy));
        when(onboardingRepository.save(any(EmployeeOnboarding.class))).thenAnswer(inv -> {
            EmployeeOnboarding o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        EmployeeOnboardingResponse response = service.create(new CreateOnboardingRequest(
                5L, LocalDate.of(2026, 9, 1), "buddy-uuid"), 7L);

        ArgumentCaptor<EmployeeOnboarding> captor = ArgumentCaptor.forClass(EmployeeOnboarding.class);
        verify(onboardingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OnboardingStatus.NOT_STARTED);
        assertThat(captor.getValue().getBuddyId()).isEqualTo(77L);
        assertThat(response.employeeCode()).isEqualTo("EMP-050");
    }

    @Test
    void create_withOpenOnboarding_throwsConflict() {
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee(5L)));
        when(onboardingRepository.existsByEmployeeIdAndStatusIn(eq(5L), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateOnboardingRequest(5L, LocalDate.now(), null), 7L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(onboardingRepository, never()).save(any());
    }

    @Test
    void update_toCompleted_stampsCompletedAtOnce_andSerializesChecklist() {
        EmployeeOnboarding o = onboarding(3L, OnboardingStatus.IN_PROGRESS);
        when(onboardingRepository.findWithEmployeeById(3L)).thenReturn(Optional.of(o));

        EmployeeOnboardingResponse response = service.update(3L, new UpdateOnboardingRequest(
                LocalDate.of(2026, 9, 1), null, OnboardingStatus.COMPLETED,
                List.of(new UpdateOnboardingRequest.ChecklistItemInput("Laptop issued", true),
                        new UpdateOnboardingRequest.ChecklistItemInput("Orientation", true)),
                "All set."), 7L);

        assertThat(o.getStatus()).isEqualTo(OnboardingStatus.COMPLETED);
        assertThat(o.getCompletedAt()).isNotNull();
        assertThat(response.checklist()).hasSize(2);

        java.time.Instant firstCompletion = o.getCompletedAt();
        service.update(3L, new UpdateOnboardingRequest(
                LocalDate.of(2026, 9, 1), null, OnboardingStatus.COMPLETED, null, null), 7L);
        assertThat(o.getCompletedAt()).isEqualTo(firstCompletion);
    }

    @Test
    void get_unknownId_throwsNotFound() {
        when(onboardingRepository.findWithEmployeeById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPLOYEE_ONBOARDING_NOT_FOUND);
    }

    @Test
    void update_unknownBuddy_throwsNotFound() {
        EmployeeOnboarding o = onboarding(3L, OnboardingStatus.NOT_STARTED);
        when(onboardingRepository.findWithEmployeeById(3L)).thenReturn(Optional.of(o));
        when(userRepository.findByUuid("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(3L, new UpdateOnboardingRequest(
                LocalDate.now(), "ghost", OnboardingStatus.IN_PROGRESS, null, null), 7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
