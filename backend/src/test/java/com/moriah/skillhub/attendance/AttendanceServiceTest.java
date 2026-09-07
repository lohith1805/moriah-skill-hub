package com.moriah.skillhub.attendance;

import com.moriah.skillhub.attendance.dto.AttendanceResponse;
import com.moriah.skillhub.attendance.dto.CheckinRequest;
import com.moriah.skillhub.attendance.dto.OverrideAttendanceRequest;
import com.moriah.skillhub.attendance.entity.Attendance;
import com.moriah.skillhub.attendance.entity.AttendanceStatus;
import com.moriah.skillhub.attendance.entity.Standup;
import com.moriah.skillhub.attendance.entity.StandupStatus;
import com.moriah.skillhub.attendance.repository.AttendanceRepository;
import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code StandupService}/{@code BatchService} are mocked entirely — their own logic has its own
 * coverage; this class only proves {@code AttendanceService} reacts correctly to what they
 * return/throw. {@code AttendanceWriter} is mocked too (package-private, same package) so the
 * concurrent double-check-in recovery path can be exercised without a real database. */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private AttendanceWriter attendanceWriter;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private BatchService batchService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StandupService standupService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AttendanceService attendanceService;

    private Batch batch;
    private User student;
    private User pm;

    @BeforeEach
    void setUp() {
        batch = new Batch();
        batch.setId(100L);
        student = new User();
        student.setId(7L);
        student.setUuid("student-uuid");
        student.setFullName("Student One");
        pm = new User();
        pm.setId(1L);
        pm.setUuid("pm-uuid");
    }

    @Test
    void checkin_beforeCutoff_recordsPresent() {
        Standup standup = standup(1L, Instant.now().minus(5, ChronoUnit.MINUTES), 15);
        when(standupService.requireStandup(1L)).thenReturn(standup);
        when(batchService.isActiveMember(100L, 7L)).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));
        when(attendanceWriter.tryInsert(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceResponse response = attendanceService.checkin(7L, 1L, new CheckinRequest(null));

        assertThat(response.status()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void checkin_afterCutoff_recordsLate() {
        Standup standup = standup(1L, Instant.now().minus(30, ChronoUnit.MINUTES), 15);
        when(standupService.requireStandup(1L)).thenReturn(standup);
        when(batchService.isActiveMember(100L, 7L)).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));
        when(attendanceWriter.tryInsert(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceResponse response = attendanceService.checkin(7L, 1L, new CheckinRequest("stuck in traffic"));

        assertThat(response.status()).isEqualTo(AttendanceStatus.LATE);
        assertThat(response.blockerNotes()).isEqualTo("stuck in traffic");
    }

    @Test
    void checkin_notActiveBatchMember_throwsForbidden() {
        Standup standup = standup(1L, Instant.now(), 15);
        when(standupService.requireStandup(1L)).thenReturn(standup);
        when(batchService.isActiveMember(100L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> attendanceService.checkin(7L, 1L, new CheckinRequest(null)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_BATCH_MEMBER);
    }

    @Test
    void checkin_cancelledStandup_throwsStandupCancelled() {
        Standup standup = standup(1L, Instant.now(), 15);
        standup.setStatus(StandupStatus.CANCELLED);
        when(standupService.requireStandup(1L)).thenReturn(standup);

        assertThatThrownBy(() -> attendanceService.checkin(7L, 1L, new CheckinRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STANDUP_CANCELLED);
    }

    @Test
    void checkin_alreadyFinalised_throwsStandupAlreadyFinalised() {
        Standup standup = standup(1L, Instant.now(), 15);
        standup.setFinalisedAt(Instant.now());
        when(standupService.requireStandup(1L)).thenReturn(standup);

        assertThatThrownBy(() -> attendanceService.checkin(7L, 1L, new CheckinRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STANDUP_ALREADY_FINALISED);
    }

    /** build-plan.md feature 13: "double check-in is idempotent, not an error" — mirrors {@code
     * SubmissionServiceTest}'s equivalent coverage for feature 12's identical shape of race. */
    @Test
    void checkin_concurrentDuplicate_recoversWinningRowInsteadOfFailing() {
        Standup standup = standup(1L, Instant.now(), 15);
        when(standupService.requireStandup(1L)).thenReturn(standup);
        when(batchService.isActiveMember(100L, 7L)).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));
        when(attendanceWriter.tryInsert(any(Attendance.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        Attendance winning = new Attendance();
        winning.setId(55L);
        winning.setStandup(standup);
        winning.setUser(student);
        winning.setStatus(AttendanceStatus.PRESENT);
        when(attendanceWriter.findExisting(1L, 7L)).thenReturn(Optional.of(winning));

        AttendanceResponse response = attendanceService.checkin(7L, 1L, new CheckinRequest(null));

        assertThat(response.id()).isEqualTo(55L);
    }

    @Test
    void override_studentNeverCheckedIn_createsRowAndAuditsNullOldValue() {
        Standup standup = standup(1L, Instant.now(), 15);
        when(standupService.requireStandup(1L)).thenReturn(standup);
        when(attendanceRepository.findByStandupIdAndUserId(1L, 7L)).thenReturn(Optional.empty());
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(userRepository.findById(1L)).thenReturn(Optional.of(pm));
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> {
            Attendance a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        AttendanceResponse response = attendanceService.override(1L, 1L,
                new OverrideAttendanceRequest("student-uuid", AttendanceStatus.ABSENT, null));

        assertThat(response.status()).isEqualTo(AttendanceStatus.ABSENT);
        verify(batchService).requireOwnerOrAdmin(1L, batch);
        verify(auditLogService).record(eq(1L), eq("ATTENDANCE_OVERRIDE"), eq("Attendance"), eq(99L),
                isNull(), eq(AttendanceStatus.ABSENT));
    }

    @Test
    void override_existingRow_updatesAndAuditsOldValue() {
        Standup standup = standup(1L, Instant.now(), 15);
        Attendance existing = new Attendance();
        existing.setId(42L);
        existing.setStandup(standup);
        existing.setUser(student);
        existing.setStatus(AttendanceStatus.ABSENT);
        existing.setAutoMarked(true);

        when(standupService.requireStandup(1L)).thenReturn(standup);
        when(attendanceRepository.findByStandupIdAndUserId(1L, 7L)).thenReturn(Optional.of(existing));
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(userRepository.findById(1L)).thenReturn(Optional.of(pm));
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceResponse response = attendanceService.override(1L, 1L,
                new OverrideAttendanceRequest("student-uuid", AttendanceStatus.PRESENT, "corrected"));

        assertThat(response.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(response.autoMarked()).isFalse();
        verify(auditLogService).record(eq(1L), eq("ATTENDANCE_OVERRIDE"), eq("Attendance"), eq(42L),
                eq(AttendanceStatus.ABSENT), eq(AttendanceStatus.PRESENT));
    }

    @Test
    void override_cancelledStandup_throwsStandupCancelled() {
        Standup standup = standup(1L, Instant.now(), 15);
        standup.setStatus(StandupStatus.CANCELLED);
        when(standupService.requireStandup(1L)).thenReturn(standup);

        assertThatThrownBy(() -> attendanceService.override(1L, 1L,
                new OverrideAttendanceRequest("student-uuid", AttendanceStatus.PRESENT, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STANDUP_CANCELLED);
    }

    private Standup standup(Long id, Instant scheduledAt, int lateCutoffMinutes) {
        Standup standup = new Standup();
        standup.setId(id);
        standup.setBatch(batch);
        standup.setScheduledAt(scheduledAt);
        standup.setLateCutoffMinutes(lateCutoffMinutes);
        standup.setStatus(StandupStatus.SCHEDULED);
        return standup;
    }
}
