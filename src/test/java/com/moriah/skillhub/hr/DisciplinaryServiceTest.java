package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.hr.dto.CreateDisciplinaryActionRequest;
import com.moriah.skillhub.hr.dto.DisciplinaryActionResponse;
import com.moriah.skillhub.hr.dto.UpdateDisciplinaryActionRequest;
import com.moriah.skillhub.hr.entity.DisciplinaryAction;
import com.moriah.skillhub.hr.entity.DisciplinaryActionType;
import com.moriah.skillhub.hr.entity.DisciplinarySeverity;
import com.moriah.skillhub.hr.entity.DisciplinaryStatus;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.repository.DisciplinaryActionRepository;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisciplinaryServiceTest {

    @Mock
    private DisciplinaryActionRepository actionRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DisciplinaryService service;

    private Employee employee(long id) {
        User u = new User();
        u.setId(90L);
        u.setFullName("Emp X");
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeCode("EMP-070");
        e.setUser(u);
        return e;
    }

    private DisciplinaryAction action(long id, DisciplinaryStatus status) {
        DisciplinaryAction d = new DisciplinaryAction();
        d.setId(id);
        d.setEmployee(employee(5L));
        d.setActionType(DisciplinaryActionType.WRITTEN_WARNING);
        d.setSeverity(DisciplinarySeverity.MEDIUM);
        d.setIncidentDate(LocalDate.of(2026, 8, 1));
        d.setDescription("Repeated lateness");
        d.setStatus(status);
        return d;
    }

    @Test
    void create_landsOpenWithCallerAsRaiser() {
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee(5L)));
        when(actionRepository.save(any(DisciplinaryAction.class))).thenAnswer(inv -> {
            DisciplinaryAction d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });

        DisciplinaryActionResponse response = service.create(new CreateDisciplinaryActionRequest(
                5L, DisciplinaryActionType.VERBAL_WARNING, DisciplinarySeverity.LOW,
                LocalDate.of(2026, 8, 10), "Missed standup 3 times"), 8L);

        ArgumentCaptor<DisciplinaryAction> captor = ArgumentCaptor.forClass(DisciplinaryAction.class);
        verify(actionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DisciplinaryStatus.OPEN);
        assertThat(captor.getValue().getRaisedBy()).isEqualTo(8L);
        assertThat(response.employeeCode()).isEqualTo("EMP-070");
    }

    @Test
    void create_unknownEmployee_throwsNotFound() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateDisciplinaryActionRequest(
                404L, DisciplinaryActionType.OTHER, DisciplinarySeverity.HIGH, LocalDate.now(), "x"), 8L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(actionRepository, never()).save(any());
    }

    @Test
    void update_advancingToResolved_stampsResolvedAtOnce() {
        DisciplinaryAction d = action(3L, DisciplinaryStatus.ACKNOWLEDGED);
        d.setAcknowledgedAt(java.time.Instant.parse("2026-08-05T00:00:00Z"));
        when(actionRepository.findWithEmployeeById(3L)).thenReturn(Optional.of(d));

        service.update(3L, new UpdateDisciplinaryActionRequest(
                DisciplinaryActionType.WRITTEN_WARNING, DisciplinarySeverity.MEDIUM, LocalDate.of(2026, 8, 1),
                "Repeated lateness", "Final written warning issued", DisciplinaryStatus.RESOLVED,
                "Improved after warning."), 8L);

        assertThat(d.getStatus()).isEqualTo(DisciplinaryStatus.RESOLVED);
        assertThat(d.getResolvedAt()).isNotNull();
        assertThat(d.getAcknowledgedAt()).isEqualTo(java.time.Instant.parse("2026-08-05T00:00:00Z"));

        java.time.Instant firstResolved = d.getResolvedAt();
        service.update(3L, new UpdateDisciplinaryActionRequest(
                DisciplinaryActionType.WRITTEN_WARNING, DisciplinarySeverity.MEDIUM, LocalDate.of(2026, 8, 1),
                "Repeated lateness", "x", DisciplinaryStatus.RESOLVED, "y"), 8L);
        assertThat(d.getResolvedAt()).isEqualTo(firstResolved);
    }

    @Test
    void update_toAcknowledged_stampsAcknowledgedAt() {
        DisciplinaryAction d = action(3L, DisciplinaryStatus.OPEN);
        when(actionRepository.findWithEmployeeById(3L)).thenReturn(Optional.of(d));

        service.update(3L, new UpdateDisciplinaryActionRequest(
                DisciplinaryActionType.WRITTEN_WARNING, DisciplinarySeverity.MEDIUM, LocalDate.of(2026, 8, 1),
                "Repeated lateness", null, DisciplinaryStatus.ACKNOWLEDGED, null), 8L);

        assertThat(d.getAcknowledgedAt()).isNotNull();
        assertThat(d.getResolvedAt()).isNull();
    }

    @Test
    void get_unknownId_throwsNotFound() {
        when(actionRepository.findWithEmployeeById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
