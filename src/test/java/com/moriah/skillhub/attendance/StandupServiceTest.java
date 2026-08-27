package com.moriah.skillhub.attendance;

import com.moriah.skillhub.attendance.dto.CreateStandupRequest;
import com.moriah.skillhub.attendance.dto.StandupResponse;
import com.moriah.skillhub.attendance.dto.UpdateStandupRequest;
import com.moriah.skillhub.attendance.entity.Standup;
import com.moriah.skillhub.attendance.entity.StandupStatus;
import com.moriah.skillhub.attendance.repository.StandupRepository;
import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code BatchService} is mocked entirely — its own ownership logic has its own coverage; this
 * class only proves {@code StandupService} delegates to it correctly. {@code jobsZone} is set via
 * {@code ReflectionTestUtils} since it's a plain {@code @Value} field, not constructor-injected
 * (Spring wires it at runtime; nothing else in this codebase needed the same technique yet). */
@ExtendWith(MockitoExtension.class)
class StandupServiceTest {

    @Mock
    private StandupRepository standupRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private BatchService batchService;

    @InjectMocks
    private StandupService standupService;

    @Captor
    private ArgumentCaptor<Instant> startAtCaptor;
    @Captor
    private ArgumentCaptor<Instant> endAtCaptor;

    private Batch batch;

    @BeforeEach
    void setUp() {
        batch = new Batch();
        batch.setId(100L);
        ReflectionTestUtils.setField(standupService, "jobsZone", "Asia/Kolkata");
    }

    @Test
    void create_happyPath_defaultsLateCutoffAndSavesScheduled() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

        StandupResponse response = standupService.create(1L,
                new CreateStandupRequest(100L, null, Instant.parse("2026-08-27T04:00:00Z"), null, "Daily sync"));

        assertThat(response.status()).isEqualTo(StandupStatus.SCHEDULED);
        assertThat(response.lateCutoffMinutes()).isEqualTo(15);
        verify(batchService).requireOwnerOrAdmin(1L, batch);
        verify(standupRepository).save(any(Standup.class));
    }

    @Test
    void create_explicitLateCutoff_usesRequestedValue() {
        when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

        StandupResponse response = standupService.create(1L,
                new CreateStandupRequest(100L, 5L, Instant.now(), 30, null));

        assertThat(response.lateCutoffMinutes()).isEqualTo(30);
        assertThat(response.sprintId()).isEqualTo(5L);
    }

    @Test
    void create_batchNotFound_throwsResourceNotFound() {
        when(batchRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> standupService.create(1L,
                new CreateStandupRequest(100L, null, Instant.now(), null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_withDate_convertsToIstDayBoundaryInstantRange() {
        when(standupRepository.search(eq(100L), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        standupService.list(100L, LocalDate.of(2026, 8, 27), PageRequest.of(0, 20));

        verify(standupRepository).search(eq(100L), startAtCaptor.capture(), endAtCaptor.capture(), any());
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        assertThat(startAtCaptor.getValue()).isEqualTo(LocalDate.of(2026, 8, 27).atStartOfDay(ist).toInstant());
        assertThat(endAtCaptor.getValue()).isEqualTo(LocalDate.of(2026, 8, 28).atStartOfDay(ist).toInstant());
    }

    @Test
    void list_noDate_passesNullRange() {
        when(standupRepository.search(eq(100L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        standupService.list(100L, null, PageRequest.of(0, 20));

        verify(standupRepository).search(eq(100L), isNull(), isNull(), any());
    }

    @Test
    void update_scheduledToCancelled_succeeds() {
        Standup standup = standup(1L, StandupStatus.SCHEDULED);
        when(standupRepository.findById(1L)).thenReturn(Optional.of(standup));

        StandupResponse response = standupService.update(1L, 1L,
                new UpdateStandupRequest("Holiday", 15, StandupStatus.CANCELLED));

        assertThat(response.status()).isEqualTo(StandupStatus.CANCELLED);
        verify(batchService).requireOwnerOrAdmin(1L, batch);
    }

    @Test
    void update_sameStatusNoOp_editsNotesOnly() {
        Standup standup = standup(1L, StandupStatus.SCHEDULED);
        when(standupRepository.findById(1L)).thenReturn(Optional.of(standup));

        StandupResponse response = standupService.update(1L, 1L,
                new UpdateStandupRequest("Updated notes", 20, StandupStatus.SCHEDULED));

        assertThat(response.status()).isEqualTo(StandupStatus.SCHEDULED);
        assertThat(response.notes()).isEqualTo("Updated notes");
        assertThat(response.lateCutoffMinutes()).isEqualTo(20);
    }

    /** `/review` regression: {@code request.notes()} is optional, and cancelling via {@code
     * {"status":"CANCELLED"}} alone (no {@code notes}) is the natural minimal request — it must
     * not silently wipe an existing note the way a missing {@code lateCutoffMinutes} already
     * doesn't wipe the existing cutoff. */
    @Test
    void update_nullNotes_preservesExistingNotes() {
        Standup standup = standup(1L, StandupStatus.SCHEDULED);
        standup.setNotes("Moved to conference room B");
        when(standupRepository.findById(1L)).thenReturn(Optional.of(standup));

        StandupResponse response = standupService.update(1L, 1L,
                new UpdateStandupRequest(null, null, StandupStatus.CANCELLED));

        assertThat(response.notes()).isEqualTo("Moved to conference room B");
        assertThat(response.status()).isEqualTo(StandupStatus.CANCELLED);
    }

    @Test
    void update_attemptToSetConducted_throwsInvalidTransition() {
        Standup standup = standup(1L, StandupStatus.SCHEDULED);
        when(standupRepository.findById(1L)).thenReturn(Optional.of(standup));

        assertThatThrownBy(() -> standupService.update(1L, 1L,
                new UpdateStandupRequest(null, null, StandupStatus.CONDUCTED)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STANDUP_INVALID_TRANSITION);
    }

    @Test
    void update_alreadyConducted_rejectsAnyEdit() {
        Standup standup = standup(1L, StandupStatus.CONDUCTED);
        when(standupRepository.findById(1L)).thenReturn(Optional.of(standup));

        assertThatThrownBy(() -> standupService.update(1L, 1L,
                new UpdateStandupRequest(null, null, StandupStatus.CANCELLED)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STANDUP_INVALID_TRANSITION);
    }

    @Test
    void update_standupNotFound_throwsResourceNotFound() {
        when(standupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> standupService.update(1L, 99L,
                new UpdateStandupRequest(null, null, StandupStatus.CANCELLED)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Standup standup(Long id, StandupStatus status) {
        Standup standup = new Standup();
        standup.setId(id);
        standup.setBatch(batch);
        standup.setScheduledAt(Instant.now());
        standup.setLateCutoffMinutes(15);
        standup.setStatus(status);
        return standup;
    }
}
