package com.moriah.skillhub.batch;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.entity.BatchStudent;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.batch.entity.PendingBatchAllocation;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.batch.repository.BatchStudentRepository;
import com.moriah.skillhub.batch.repository.PendingBatchAllocationRepository;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.common.security.EntitlementFlags;
import com.moriah.skillhub.common.security.EntitlementFlagsLoader;
import com.moriah.skillhub.user.UserService;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `/architect feature 10` decisions, proven directly: candidate ordering/fallthrough on a full
 * batch, the pending-allocation table (not just a notification), and the one-open-row guard.
 * {@code BatchFlowIT} covers the concurrency guarantee ("exactly one enrolment") for real, since
 * that needs real concurrent transactions, not mocks.
 */
@ExtendWith(MockitoExtension.class)
class BatchAllocationServiceTest {

    @Mock
    private BatchRepository batchRepository;
    @Mock
    private BatchStudentRepository batchStudentRepository;
    @Mock
    private PendingBatchAllocationRepository pendingBatchAllocationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private EntitlementFlagsLoader entitlementFlagsLoader;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BatchAllocationService batchAllocationService;

    @Captor
    private ArgumentCaptor<BatchStudent> batchStudentCaptor;
    @Captor
    private ArgumentCaptor<PendingBatchAllocation> pendingCaptor;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUuid("uuid-1");
        user.setFullName("Ada Lovelace");
    }

    @Test
    void allocate_planDoesNotAllowBatch_doesNothing() {
        when(entitlementFlagsLoader.load(1L)).thenReturn(
                new EntitlementFlags(false, false, false, false, false, false, 1));

        batchAllocationService.allocate(1L, "FULL_STACK", 10L);

        verify(entitlementFlagsLoader).evict(1L);
        verify(batchRepository, never()).findAllocationCandidates(anyString(), any(Integer.class));
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void allocate_singleMatchingCandidateWithCapacity_enrollsStudent() {
        when(entitlementFlagsLoader.load(1L)).thenReturn(
                new EntitlementFlags(true, true, true, false, false, false, 3));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Batch batch = batch(100L);
        when(batchRepository.findAllocationCandidates("FULL_STACK", 3)).thenReturn(List.of(batch));
        when(batchRepository.tryReserveSeat(100L)).thenReturn(1);
        when(pendingBatchAllocationRepository.findByUserIdAndResolvedAtIsNull(1L)).thenReturn(Optional.empty());

        batchAllocationService.allocate(1L, "FULL_STACK", 10L);

        verify(batchStudentRepository).save(batchStudentCaptor.capture());
        BatchStudent saved = batchStudentCaptor.getValue();
        assertThat(saved.getBatch()).isEqualTo(batch);
        assertThat(saved.getUser()).isEqualTo(user);
        verify(pendingBatchAllocationRepository, never()).save(any());
        verify(notificationService, never()).enqueueAfterCommit(anyLong(), any(), anyString(), anyMap());
    }

    @Test
    void allocate_firstCandidateFull_triesNextCandidateInOrder() {
        when(entitlementFlagsLoader.load(1L)).thenReturn(
                new EntitlementFlags(true, true, true, false, false, false, 3));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Batch full = batch(100L);
        Batch hasRoom = batch(200L);
        when(batchRepository.findAllocationCandidates("FULL_STACK", 3)).thenReturn(List.of(full, hasRoom));
        when(batchRepository.tryReserveSeat(100L)).thenReturn(0); // raced full
        when(batchRepository.tryReserveSeat(200L)).thenReturn(1);
        when(pendingBatchAllocationRepository.findByUserIdAndResolvedAtIsNull(1L)).thenReturn(Optional.empty());

        batchAllocationService.allocate(1L, "FULL_STACK", 10L);

        verify(batchRepository).tryReserveSeat(100L);
        verify(batchRepository).tryReserveSeat(200L);
        verify(batchStudentRepository).save(batchStudentCaptor.capture());
        assertThat(batchStudentCaptor.getValue().getBatch()).isEqualTo(hasRoom);
    }

    @Test
    void allocate_noCandidatesMatch_parksPendingAndNotifiesEveryTrainerPm() {
        when(entitlementFlagsLoader.load(1L)).thenReturn(
                new EntitlementFlags(true, true, true, false, false, false, 3));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(batchRepository.findAllocationCandidates("FULL_STACK", 3)).thenReturn(List.of());
        when(pendingBatchAllocationRepository.findByUserIdAndResolvedAtIsNull(1L)).thenReturn(Optional.empty());
        when(userService.findUserIdsByRole(RoleCode.TRAINER_PM)).thenReturn(List.of(50L, 51L));

        batchAllocationService.allocate(1L, "FULL_STACK", 10L);

        verify(pendingBatchAllocationRepository).save(pendingCaptor.capture());
        PendingBatchAllocation pending = pendingCaptor.getValue();
        assertThat(pending.getUser()).isEqualTo(user);
        assertThat(pending.getTrackCode()).isEqualTo("FULL_STACK");
        assertThat(pending.getPlanId()).isEqualTo(10L);

        verify(notificationService).enqueueAfterCommit(eq(50L), eq(NotificationChannel.IN_APP),
                eq("BATCH_ALLOCATION_PENDING"), anyMap());
        verify(notificationService).enqueueAfterCommit(eq(51L), eq(NotificationChannel.IN_APP),
                eq("BATCH_ALLOCATION_PENDING"), anyMap());
    }

    @Test
    void allocate_alreadyHasAnOpenPendingRow_doesNotCreateADuplicateOrNotify() {
        when(entitlementFlagsLoader.load(1L)).thenReturn(
                new EntitlementFlags(true, true, true, false, false, false, 3));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(batchRepository.findAllocationCandidates("FULL_STACK", 3)).thenReturn(List.of());
        PendingBatchAllocation existing = new PendingBatchAllocation();
        when(pendingBatchAllocationRepository.findByUserIdAndResolvedAtIsNull(1L)).thenReturn(Optional.of(existing));

        batchAllocationService.allocate(1L, "FULL_STACK", 10L);

        verify(pendingBatchAllocationRepository, never()).save(any());
        verify(userService, never()).findUserIdsByRole(any());
        verify(notificationService, never()).enqueueAfterCommit(anyLong(), any(), anyString(), anyMap());
    }

    @Test
    void allocate_resolvesAnExistingOpenPendingRowOnSuccessfulEnrollment() {
        when(entitlementFlagsLoader.load(1L)).thenReturn(
                new EntitlementFlags(true, true, true, false, false, false, 3));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Batch batch = batch(100L);
        when(batchRepository.findAllocationCandidates("FULL_STACK", 3)).thenReturn(List.of(batch));
        when(batchRepository.tryReserveSeat(100L)).thenReturn(1);
        PendingBatchAllocation existing = new PendingBatchAllocation();
        when(pendingBatchAllocationRepository.findByUserIdAndResolvedAtIsNull(1L)).thenReturn(Optional.of(existing));

        batchAllocationService.allocate(1L, "FULL_STACK", 10L);

        assertThat(existing.getResolvedAt()).isNotNull();
        assertThat(existing.getResolvedBatch()).isEqualTo(batch);
    }

    @Test
    void deallocate_activeEnrollment_setsReassignedAndReleasesTheSeat() {
        Batch batch = batch(100L);
        BatchStudent batchStudent = new BatchStudent();
        batchStudent.setBatch(batch);
        batchStudent.setUser(user);
        batchStudent.setStatus(BatchStudentStatus.ACTIVE);
        when(batchStudentRepository.findByUserIdAndStatusIn(1L,
                        List.of(BatchStudentStatus.ACTIVE, BatchStudentStatus.ON_PIP)))
                .thenReturn(Optional.of(batchStudent));

        batchAllocationService.deallocate(1L);

        assertThat(batchStudent.getStatus()).isEqualTo(BatchStudentStatus.REASSIGNED);
        verify(batchRepository).releaseSeat(100L);
    }

    @Test
    void deallocate_noActiveEnrollment_doesNothing() {
        when(batchStudentRepository.findByUserIdAndStatusIn(1L,
                        List.of(BatchStudentStatus.ACTIVE, BatchStudentStatus.ON_PIP)))
                .thenReturn(Optional.empty());

        batchAllocationService.deallocate(1L);

        verify(batchRepository, never()).releaseSeat(anyLong());
    }

    private Batch batch(Long id) {
        Batch batch = new Batch();
        batch.setId(id);
        batch.setName("Batch " + id);
        batch.setTrackCode("FULL_STACK");
        return batch;
    }
}
