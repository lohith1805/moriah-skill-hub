package com.moriah.skillhub.client;

import com.moriah.skillhub.client.entity.BaMeeting;
import com.moriah.skillhub.client.entity.BaMeetingStatus;
import com.moriah.skillhub.client.repository.BaMeetingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The user's own ask: a Client Pre-Project Discussion should mark itself over once its
 * scheduled time has passed, instead of sitting {@code SCHEDULED} forever. */
@ExtendWith(MockitoExtension.class)
class BaMeetingAutoCompleteServiceTest {

    @Mock
    private BaMeetingRepository meetingRepository;

    @InjectMocks
    private BaMeetingAutoCompleteService service;

    private BaMeeting meeting(long id, Instant scheduledAt, Integer durationMinutes) {
        BaMeeting m = new BaMeeting();
        m.setId(id);
        m.setStatus(BaMeetingStatus.SCHEDULED);
        m.setScheduledAt(scheduledAt);
        m.setDurationMinutes(durationMinutes);
        return m;
    }

    @Test
    void meetingWhoseEndTimeHasPassed_isMarkedCompleted() {
        BaMeeting overdue = meeting(1L, Instant.now().minus(2, ChronoUnit.HOURS), 30);
        when(meetingRepository.findByStatus(BaMeetingStatus.SCHEDULED)).thenReturn(List.of(overdue));

        int count = service.autoComplete();

        assertThat(count).isEqualTo(1);
        assertThat(overdue.getStatus()).isEqualTo(BaMeetingStatus.COMPLETED);
        verify(meetingRepository).saveAll(List.of(overdue));
    }

    @Test
    void meetingStillInProgressOrUpcoming_isLeftScheduled() {
        BaMeeting upcoming = meeting(1L, Instant.now().plus(1, ChronoUnit.HOURS), 60);
        BaMeeting inProgress = meeting(2L, Instant.now().minus(10, ChronoUnit.MINUTES), 60);
        when(meetingRepository.findByStatus(BaMeetingStatus.SCHEDULED)).thenReturn(List.of(upcoming, inProgress));

        int count = service.autoComplete();

        assertThat(count).isEqualTo(0);
        assertThat(upcoming.getStatus()).isEqualTo(BaMeetingStatus.SCHEDULED);
        assertThat(inProgress.getStatus()).isEqualTo(BaMeetingStatus.SCHEDULED);
        verify(meetingRepository, never()).saveAll(any());
    }

    @Test
    void nullDurationMinutes_fallsBackToSixtyMinutes() {
        // Scheduled 90 minutes ago with no duration set — falls back to 60, so it's overdue.
        BaMeeting overdue = meeting(1L, Instant.now().minus(90, ChronoUnit.MINUTES), null);
        when(meetingRepository.findByStatus(BaMeetingStatus.SCHEDULED)).thenReturn(List.of(overdue));

        int count = service.autoComplete();

        assertThat(count).isEqualTo(1);
        assertThat(overdue.getStatus()).isEqualTo(BaMeetingStatus.COMPLETED);
    }
}
