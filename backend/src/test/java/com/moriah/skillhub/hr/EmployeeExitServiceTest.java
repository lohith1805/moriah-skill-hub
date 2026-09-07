package com.moriah.skillhub.hr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moriah.skillhub.common.audit.AuditLogService;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeExitServiceTest {

    @Mock
    private EmployeeExitRepository exitRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private EmployeeExitService service;

    private Employee employee(long id) {
        User u = new User();
        u.setId(90L);
        u.setFullName("Exiting Emp");
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeCode("EMP-042");
        e.setUser(u);
        e.setStatus(EmployeeStatus.ACTIVE);
        return e;
    }

    private EmployeeExit exit(long id, ExitType type, EmployeeExitStatus status) {
        EmployeeExit x = new EmployeeExit();
        x.setId(id);
        x.setEmployee(employee(5L));
        x.setExitType(type);
        x.setLastWorkingDay(LocalDate.of(2026, 10, 31));
        x.setStatus(status);
        return x;
    }

    @Test
    void initiate_createsInitiatedRecord() {
        Employee e = employee(5L);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(e));
        when(exitRepository.existsByEmployeeIdAndStatusIn(eq(5L), anyCollection())).thenReturn(false);
        when(exitRepository.save(any(EmployeeExit.class))).thenAnswer(inv -> {
            EmployeeExit x = inv.getArgument(0);
            x.setId(1L);
            return x;
        });

        EmployeeExitResponse response = service.initiate(new InitiateEmployeeExitRequest(
                5L, ExitType.RESIGNATION, LocalDate.of(2026, 10, 31), "New opportunity", 60), 7L);

        ArgumentCaptor<EmployeeExit> captor = ArgumentCaptor.forClass(EmployeeExit.class);
        verify(exitRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(EmployeeExitStatus.INITIATED);
        assertThat(captor.getValue().getInitiatedBy()).isEqualTo(7L);
        assertThat(response.employeeCode()).isEqualTo("EMP-042");
    }

    @Test
    void initiate_withExistingOpenExit_throwsConflict() {
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee(5L)));
        when(exitRepository.existsByEmployeeIdAndStatusIn(eq(5L), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.initiate(new InitiateEmployeeExitRequest(
                5L, ExitType.RESIGNATION, LocalDate.now(), null, null), 7L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(exitRepository, never()).save(any());
    }

    @Test
    void initiate_unknownEmployee_throwsNotFound() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate(new InitiateEmployeeExitRequest(
                404L, ExitType.OTHER, LocalDate.now(), null, null), 7L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPLOYEE_NOT_FOUND);
    }

    @Test
    void update_serializesChecklistAndKeepsWorkingStatus() {
        EmployeeExit x = exit(3L, ExitType.RESIGNATION, EmployeeExitStatus.INITIATED);
        when(exitRepository.findWithEmployeeById(3L)).thenReturn(Optional.of(x));

        EmployeeExitResponse response = service.update(3L, new UpdateEmployeeExitRequest(
                ExitType.RESIGNATION, LocalDate.of(2026, 11, 15), "reason", 30, EmployeeExitStatus.IN_PROGRESS,
                List.of(new UpdateEmployeeExitRequest.ClearanceItemInput("Return laptop", true),
                        new UpdateEmployeeExitRequest.ClearanceItemInput("Knowledge transfer", false)),
                "Positive exit interview."), 7L);

        assertThat(x.getStatus()).isEqualTo(EmployeeExitStatus.IN_PROGRESS);
        assertThat(response.clearanceChecklist()).hasSize(2);
        assertThat(response.clearanceChecklist().get(0).label()).isEqualTo("Return laptop");
        assertThat(response.clearanceChecklist().get(0).done()).isTrue();
    }

    @Test
    void update_rejectsCompletedStatusAtConstruction() {
        assertThatThrownBy(() -> new UpdateEmployeeExitRequest(
                ExitType.OTHER, LocalDate.now(), null, null, EmployeeExitStatus.COMPLETED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void complete_resignation_setsEmployeeExitedAndStampsDateOfExit() {
        EmployeeExit x = exit(3L, ExitType.RESIGNATION, EmployeeExitStatus.IN_PROGRESS);
        when(exitRepository.findWithEmployeeById(3L)).thenReturn(Optional.of(x));

        service.complete(3L, 7L);

        assertThat(x.getStatus()).isEqualTo(EmployeeExitStatus.COMPLETED);
        assertThat(x.getCompletedAt()).isNotNull();
        assertThat(x.getEmployee().getStatus()).isEqualTo(EmployeeStatus.EXITED);
        assertThat(x.getEmployee().getDateOfExit()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void complete_termination_setsEmployeeTerminated() {
        EmployeeExit x = exit(3L, ExitType.TERMINATION, EmployeeExitStatus.INITIATED);
        when(exitRepository.findWithEmployeeById(3L)).thenReturn(Optional.of(x));

        service.complete(3L, 7L);

        assertThat(x.getEmployee().getStatus()).isEqualTo(EmployeeStatus.TERMINATED);
    }

    @Test
    void complete_terminatesLoginAccountAndKillsSessions() {
        EmployeeExit x = exit(3L, ExitType.RESIGNATION, EmployeeExitStatus.IN_PROGRESS);
        User u = x.getEmployee().getUser();
        u.setStatus(UserStatus.ACTIVE);
        int tokenVersionBefore = u.getTokenVersion();
        when(exitRepository.findWithEmployeeById(3L)).thenReturn(Optional.of(x));

        service.complete(3L, 7L);

        assertThat(u.getStatus()).isEqualTo(UserStatus.TERMINATED);
        assertThat(u.getTokenVersion()).isEqualTo(tokenVersionBefore + 1);
        verify(userRepository).save(u);
    }

    @Test
    void complete_alreadyCompleted_throwsConflict() {
        EmployeeExit x = exit(3L, ExitType.RESIGNATION, EmployeeExitStatus.COMPLETED);
        when(exitRepository.findWithEmployeeById(3L)).thenReturn(Optional.of(x));

        assertThatThrownBy(() -> service.complete(3L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
    }
}
