package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import com.moriah.skillhub.hr.dto.CreateLeaveRequest;
import com.moriah.skillhub.hr.dto.LeaveDecisionRequest;
import com.moriah.skillhub.hr.dto.LeaveRequestResponse;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.LeaveRequest;
import com.moriah.skillhub.hr.entity.LeaveStatus;
import com.moriah.skillhub.hr.entity.LeaveType;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.hr.repository.LeaveRequestRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    // Built per-test, not as a field initializer — a field initializer runs during construction,
    // before MockitoExtension's beforeEach injects @Mock fields, leaving them null.
    private LeaveService service() {
        return new LeaveService(leaveRequestRepository, employeeRepository, userRepository, auditLogService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId, List<String> roles) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, "uuid-" + userId, roles);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        user.setUuid("uuid-" + id);
        return user;
    }

    // --- create ---

    @Test
    void create_callerHasEmployeeRecord_savesAndComputesDays() {
        when(employeeRepository.existsByUserId(1L)).thenReturn(true);
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L));

        LeaveRequestResponse response = service().create(
                new CreateLeaveRequest(LeaveType.SICK, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3), "Fever"),
                1L);

        assertThat(response.days()).isEqualByComparingTo("3");
        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    void create_callerHasNoEmployeeRecord_throwsNotFound() {
        when(employeeRepository.existsByUserId(1L)).thenReturn(false);

        assertThatThrownBy(() -> service().create(
                new CreateLeaveRequest(LeaveType.SICK, LocalDate.now(), LocalDate.now(), null), 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPLOYEE_NOT_FOUND);
    }

    // --- decide ---

    private LeaveRequest pendingLeave(long requesterId) {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(1L);
        leave.setUser(user(requesterId));
        leave.setFromDate(LocalDate.of(2026, 3, 1));
        leave.setToDate(LocalDate.of(2026, 3, 3));
        leave.setStatus(LeaveStatus.PENDING);
        return leave;
    }

    @Test
    void decide_callerIsReportingManager_approves() {
        LeaveRequest leave = pendingLeave(1L);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leave));
        authenticateAs(5L, List.of("TRAINER_PM"));

        Employee manager = new Employee();
        manager.setId(10L);
        manager.setUser(user(5L));
        Employee requesterEmployee = new Employee();
        requesterEmployee.setId(11L);
        requesterEmployee.setReportingManager(manager);
        when(employeeRepository.findByUserId(1L)).thenReturn(Optional.of(requesterEmployee));
        when(leaveRequestRepository.existsOverlappingApproved(1L, leave.getFromDate(), leave.getToDate(), 1L)).thenReturn(false);
        when(userRepository.getReferenceById(5L)).thenReturn(user(5L));

        LeaveRequestResponse response = service().decide(1L, new LeaveDecisionRequest(LeaveStatus.APPROVED), 5L);

        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    void decide_callerIsHrManager_approvesWithoutBeingReportingManager() {
        LeaveRequest leave = pendingLeave(1L);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leave));
        authenticateAs(9L, List.of("HR_MANAGER"));
        when(leaveRequestRepository.existsOverlappingApproved(1L, leave.getFromDate(), leave.getToDate(), 1L)).thenReturn(false);
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));

        LeaveRequestResponse response = service().decide(1L, new LeaveDecisionRequest(LeaveStatus.APPROVED), 9L);

        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    void decide_callerIsRequesterEvenAsHrManager_throwsForbidden() {
        LeaveRequest leave = pendingLeave(1L);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leave));
        authenticateAs(1L, List.of("HR_MANAGER"));

        assertThatThrownBy(() -> service().decide(1L, new LeaveDecisionRequest(LeaveStatus.APPROVED), 1L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SELF_DECISION_NOT_ALLOWED);
    }

    @Test
    void decide_callerIsUnrelatedUser_throwsForbidden() {
        LeaveRequest leave = pendingLeave(1L);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leave));
        authenticateAs(7L, List.of("DEVELOPER"));
        when(employeeRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().decide(1L, new LeaveDecisionRequest(LeaveStatus.APPROVED), 7L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_REPORTING_MANAGER);
    }

    @Test
    void decide_approvingOverlapsAnotherApprovedLeave_throws409() {
        LeaveRequest leave = pendingLeave(1L);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leave));
        authenticateAs(9L, List.of("HR_MANAGER"));
        when(leaveRequestRepository.existsOverlappingApproved(1L, leave.getFromDate(), leave.getToDate(), 1L)).thenReturn(true);

        assertThatThrownBy(() -> service().decide(1L, new LeaveDecisionRequest(LeaveStatus.APPROVED), 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEAVE_OVERLAPS_APPROVED_LEAVE);
    }

    @Test
    void decide_rejecting_skipsOverlapCheck() {
        LeaveRequest leave = pendingLeave(1L);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leave));
        authenticateAs(9L, List.of("HR_MANAGER"));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L));

        LeaveRequestResponse response = service().decide(1L, new LeaveDecisionRequest(LeaveStatus.REJECTED), 9L);

        assertThat(response.status()).isEqualTo(LeaveStatus.REJECTED);
    }

    @Test
    void decide_alreadyDecided_throwsAlreadyDecided() {
        LeaveRequest leave = pendingLeave(1L);
        leave.setStatus(LeaveStatus.APPROVED);
        when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(leave));

        assertThatThrownBy(() -> service().decide(1L, new LeaveDecisionRequest(LeaveStatus.REJECTED), 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEAVE_REQUEST_ALREADY_DECIDED);
    }

    @Test
    void decide_unknownLeave_throwsNotFound() {
        when(leaveRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().decide(99L, new LeaveDecisionRequest(LeaveStatus.APPROVED), 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LEAVE_REQUEST_NOT_FOUND);
    }
}
