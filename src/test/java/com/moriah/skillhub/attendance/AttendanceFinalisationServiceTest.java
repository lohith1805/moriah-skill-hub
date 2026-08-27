package com.moriah.skillhub.attendance;

import com.moriah.skillhub.attendance.dto.AttendanceKeyProjection;
import com.moriah.skillhub.attendance.entity.Attendance;
import com.moriah.skillhub.attendance.entity.AttendanceStatus;
import com.moriah.skillhub.attendance.entity.Standup;
import com.moriah.skillhub.attendance.entity.StandupStatus;
import com.moriah.skillhub.attendance.repository.AttendanceRepository;
import com.moriah.skillhub.attendance.repository.StandupRepository;
import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.dto.ActiveMemberProjection;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** build-plan.md feature 13: "Without this job the 75% rule ... can never fire." Tests the
 * {@code @Transactional} core directly on {@link AttendanceFinalisationService} — a separate bean
 * from {@link AttendanceFinalisationJob} precisely so that {@code @Transactional} actually applies
 * on the real {@code @Scheduled} trigger (see that class's Javadoc); this test exercises the same
 * method a unit test calling it on the job bean previously did, just relocated. */
@ExtendWith(MockitoExtension.class)
class AttendanceFinalisationServiceTest {

    @Mock
    private StandupRepository standupRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private BatchService batchService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AttendanceFinalisationService service;

    @Captor
    private ArgumentCaptor<List<Attendance>> absentRowsCaptor;

    private Batch batch;

    private Batch batch() {
        Batch batch = new Batch();
        batch.setId(100L);
        return batch;
    }

    private Standup standup(Long id, StandupStatus status) {
        Standup standup = new Standup();
        standup.setId(id);
        standup.setBatch(batch);
        standup.setScheduledAt(Instant.now().minus(2, ChronoUnit.HOURS));
        standup.setLateCutoffMinutes(15);
        standup.setStatus(status);
        return standup;
    }

    @Test
    void finalise_scheduledStandupPastDue_autoConductsAndWritesAbsentForMissingStudents() {
        batch = batch();
        Standup standup = standup(1L, StandupStatus.SCHEDULED);
        when(standupRepository.findEligibleForFinalisation(any())).thenReturn(List.of(standup));
        when(batchService.activeMembersOf(Set.of(100L))).thenReturn(List.of(
                new ActiveMemberProjection(100L, 7L),
                new ActiveMemberProjection(100L, 8L)));
        // Student 7 already checked in; student 8 never did.
        when(attendanceRepository.findKeysByStandupIds(List.of(1L)))
                .thenReturn(List.of(new AttendanceKeyProjection(1L, 7L)));
        when(userRepository.getReferenceById(8L)).thenReturn(userRef(8L));

        int count = service.finalise();

        assertThat(count).isEqualTo(1);
        assertThat(standup.getStatus()).isEqualTo(StandupStatus.CONDUCTED);
        assertThat(standup.getFinalisedAt()).isNotNull();

        verify(attendanceRepository).saveAll(absentRowsCaptor.capture());
        List<Attendance> absentRows = absentRowsCaptor.getValue();
        assertThat(absentRows).hasSize(1);
        assertThat(absentRows.get(0).getUser().getId()).isEqualTo(8L);
        assertThat(absentRows.get(0).getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(absentRows.get(0).isAutoMarked()).isTrue();
        verify(standupRepository).saveAll(List.of(standup));
    }

    @Test
    void finalise_alreadyConductedStandup_doesNotChangeStatusButStillFinalises() {
        batch = batch();
        Standup standup = standup(1L, StandupStatus.CONDUCTED);
        when(standupRepository.findEligibleForFinalisation(any())).thenReturn(List.of(standup));
        when(batchService.activeMembersOf(Set.of(100L))).thenReturn(List.of());
        when(attendanceRepository.findKeysByStandupIds(List.of(1L))).thenReturn(List.of());

        service.finalise();

        assertThat(standup.getStatus()).isEqualTo(StandupStatus.CONDUCTED);
        assertThat(standup.getFinalisedAt()).isNotNull();
    }

    @Test
    void finalise_everyStudentAlreadyHasARow_writesNoAbsentRows() {
        batch = batch();
        Standup standup = standup(1L, StandupStatus.SCHEDULED);
        when(standupRepository.findEligibleForFinalisation(any())).thenReturn(List.of(standup));
        when(batchService.activeMembersOf(Set.of(100L))).thenReturn(List.of(new ActiveMemberProjection(100L, 7L)));
        when(attendanceRepository.findKeysByStandupIds(List.of(1L)))
                .thenReturn(List.of(new AttendanceKeyProjection(1L, 7L)));

        service.finalise();

        verify(attendanceRepository).saveAll(List.of());
        verify(userRepository, never()).getReferenceById(anyLong());
    }

    @Test
    void finalise_noEligibleStandups_returnsZeroAndTouchesNothingElse() {
        when(standupRepository.findEligibleForFinalisation(any())).thenReturn(List.of());

        int count = service.finalise();

        assertThat(count).isZero();
        verify(batchService, never()).activeMembersOf(any());
        verify(attendanceRepository, never()).saveAll(any());
        verify(standupRepository, never()).saveAll(any());
    }

    private User userRef(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
