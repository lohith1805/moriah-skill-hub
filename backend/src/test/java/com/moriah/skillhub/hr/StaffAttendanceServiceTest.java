package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import com.moriah.skillhub.hr.dto.MarkStaffAttendanceRequest;
import com.moriah.skillhub.hr.dto.StaffAttendanceResponse;
import com.moriah.skillhub.hr.dto.StaffCheckinRequest;
import com.moriah.skillhub.hr.entity.StaffAttendance;
import com.moriah.skillhub.hr.entity.StaffAttendanceStatus;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.hr.repository.StaffAttendanceRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffAttendanceServiceTest {

    @Mock private StaffAttendanceRepository staffAttendanceRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    private StaffAttendanceService service() {
        StaffAttendanceService s = new StaffAttendanceService(
                staffAttendanceRepository, employeeRepository, userRepository, auditLogService);
        ReflectionTestUtils.setField(s, "zoneId", "Asia/Kolkata");
        return s;
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
        User u = new User();
        u.setId(id);
        u.setUuid("uuid-" + id);
        u.setFullName("User " + id);
        return u;
    }

    // --- self check-in ---

    @Test
    void checkin_self_createsRowAndStamps() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(employeeRepository.existsByUserId(1L)).thenReturn(true);
        when(staffAttendanceRepository.findByUserIdAndWorkDate(eq(1L), any())).thenReturn(Optional.empty());
        when(staffAttendanceRepository.save(any(StaffAttendance.class))).thenAnswer(i -> i.getArgument(0));

        StaffAttendanceResponse res = service().checkin(1L, new StaffCheckinRequest(null, "WEB-AUTH-PORTAL"));

        assertThat(res.checkedInAt()).isNotNull();
        assertThat(res.status()).isIn(StaffAttendanceStatus.PRESENT, StaffAttendanceStatus.LATE);
        assertThat(res.device()).isEqualTo("WEB-AUTH-PORTAL");
        assertThat(res.markedByUuid()).isNull(); // self check-in
    }

    @Test
    void checkin_isIdempotent_secondCallDoesNotRestamp() {
        StaffAttendance existing = new StaffAttendance();
        existing.setId(9L);
        existing.setUser(user(1L));
        existing.setWorkDate(LocalDate.now());
        existing.setCheckedInAt(java.time.Instant.parse("2026-03-01T04:00:00Z"));
        existing.setStatus(StaffAttendanceStatus.PRESENT);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(employeeRepository.existsByUserId(1L)).thenReturn(true);
        when(staffAttendanceRepository.findByUserIdAndWorkDate(eq(1L), any())).thenReturn(Optional.of(existing));

        service().checkin(1L, new StaffCheckinRequest(null, "BIO-GATE-01"));

        verify(staffAttendanceRepository, never()).save(any());
    }

    @Test
    void checkin_noEmployeeRecord_throwsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(employeeRepository.existsByUserId(1L)).thenReturn(false);

        assertThatThrownBy(() -> service().checkin(1L, new StaffCheckinRequest(null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMPLOYEE_NOT_FOUND);
    }

    // --- check-in on behalf of another ---

    @Test
    void checkin_onBehalf_nonHr_forbidden() {
        authenticateAs(1L, List.of("TRAINER_PM"));
        when(userRepository.findByUuid("uuid-2")).thenReturn(Optional.of(user(2L)));

        assertThatThrownBy(() -> service().checkin(1L, new StaffCheckinRequest("uuid-2", "BIO-GATE-01")))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_ROLE);
    }

    @Test
    void checkin_onBehalf_asHr_recordsMarkedBy() {
        authenticateAs(1L, List.of("HR_MANAGER"));
        when(userRepository.findByUuid("uuid-2")).thenReturn(Optional.of(user(2L)));
        when(employeeRepository.existsByUserId(2L)).thenReturn(true);
        when(staffAttendanceRepository.findByUserIdAndWorkDate(eq(2L), any())).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L));
        when(staffAttendanceRepository.save(any(StaffAttendance.class))).thenAnswer(i -> i.getArgument(0));

        StaffAttendanceResponse res = service().checkin(1L, new StaffCheckinRequest("uuid-2", "BIO-GATE-01"));

        assertThat(res.userUuid()).isEqualTo("uuid-2");
        assertThat(res.markedByUuid()).isEqualTo("uuid-1");
    }

    // --- HR override ---

    @Test
    void mark_upsertsStatusAndAudits() {
        authenticateAs(1L, List.of("HR_MANAGER"));
        when(userRepository.findByUuid("uuid-2")).thenReturn(Optional.of(user(2L)));
        when(employeeRepository.existsByUserId(2L)).thenReturn(true);
        when(staffAttendanceRepository.findByUserIdAndWorkDate(eq(2L), any())).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L));
        when(staffAttendanceRepository.save(any(StaffAttendance.class))).thenAnswer(i -> {
            StaffAttendance a = i.getArgument(0);
            a.setId(5L);
            return a;
        });

        StaffAttendanceResponse res = service().mark(1L, new MarkStaffAttendanceRequest(
                "uuid-2", LocalDate.of(2026, 3, 4), StaffAttendanceStatus.ABSENT, "No show"));

        assertThat(res.status()).isEqualTo(StaffAttendanceStatus.ABSENT);
        assertThat(res.notes()).isEqualTo("No show");
        verify(auditLogService).record(eq(1L), eq("STAFF_ATTENDANCE_OVERRIDE"), eq("StaffAttendance"),
                eq(5L), any(), eq(StaffAttendanceStatus.ABSENT));
    }
}
