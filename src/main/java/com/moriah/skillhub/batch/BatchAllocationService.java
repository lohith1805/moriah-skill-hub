package com.moriah.skillhub.batch;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.entity.BatchStudent;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.batch.entity.PendingBatchAllocation;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.batch.repository.BatchStudentRepository;
import com.moriah.skillhub.batch.repository.PendingBatchAllocationRepository;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.common.security.EntitlementFlags;
import com.moriah.skillhub.common.security.EntitlementFlagsLoader;
import com.moriah.skillhub.user.UserService;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Clears the feature 07 stub ({@code PaymentWebhookService} calls {@link #allocate} at the
 * marked spot). build-plan.md feature 10: "finds an ACTIVE/PLANNED batch matching track and
 * minimum tier with free capacity... Only PROJECT_BASED and above allocated... No matching batch
 * -> pending queue, PM notified. Never silently unallocated." `/architect feature 10` decisions:
 * {@code trackCode} comes from the payment (checkout-time), not the profile; "pending queue" is
 * the real {@link PendingBatchAllocation} table, not just a notification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchAllocationService {

    private final BatchRepository batchRepository;
    private final BatchStudentRepository batchStudentRepository;
    private final PendingBatchAllocationRepository pendingBatchAllocationRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EntitlementFlagsLoader entitlementFlagsLoader;
    private final NotificationService notificationService;

    @Transactional
    public void allocate(Long userId, String trackCode, Long planId) {
        entitlementFlagsLoader.evict(userId);
        EntitlementFlags flags = entitlementFlagsLoader.load(userId);

        // build-plan.md: "Only PROJECT_BASED and above allocated. STARTER and PROFESSIONAL never
        // enter a batch" — already exactly what allows_batch means (V5 seed data).
        if (!flags.allowsBatch() || flags.tierRank() == null) {
            log.info("[batch/allocate] user {} plan does not allow batch allocation, skipping", userId);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));

        List<Batch> candidates = batchRepository.findAllocationCandidates(trackCode, flags.tierRank());
        for (Batch candidate : candidates) {
            // A candidate can lose its free seat between the read above and this attempt (another
            // allocation, concurrently) — code-standards.md's conditional-atomic-update pattern:
            // 0 rows affected means try the next candidate, never retry the same row.
            if (batchRepository.tryReserveSeat(candidate.getId()) > 0) {
                enroll(user, candidate);
                return;
            }
        }

        parkPending(user, trackCode, planId);
    }

    /** build-plan.md feature 07's refund flow: "de-allocate the student from their batch." A
     * student is in at most one batch at a time (one {@code ACTIVE} subscription -> at most one
     * live allocation), so nothing beyond the user id is needed to find it. */
    @Transactional
    public void deallocate(Long userId) {
        // ON_PIP included alongside ACTIVE (feature 17 addendum, `/review` finding) — a refund
        // must still release the seat for a student currently on an open PIP, not leave their
        // batch_students row orphaned at ON_PIP with no subscription behind it.
        batchStudentRepository.findByUserIdAndStatusIn(userId,
                        List.of(BatchStudentStatus.ACTIVE, BatchStudentStatus.ON_PIP))
                .ifPresentOrElse(this::release,
                        () -> log.info("[batch/deallocate] user {} has no ACTIVE/ON_PIP batch enrollment to reverse",
                                userId));
    }

    private void release(BatchStudent batchStudent) {
        batchStudent.setStatus(BatchStudentStatus.REASSIGNED);
        batchRepository.releaseSeat(batchStudent.getBatch().getId());
    }

    private void enroll(User user, Batch batch) {
        BatchStudent batchStudent = new BatchStudent();
        batchStudent.setBatch(batch);
        batchStudent.setUser(user);
        batchStudentRepository.save(batchStudent);

        // A student who was previously parked pending (e.g. a refund-and-repurchase cycle) is
        // now resolved by this successful automatic allocation too, not just a manual PM add.
        pendingBatchAllocationRepository.findByUserIdAndResolvedAtIsNull(user.getId())
                .ifPresent(pending -> {
                    pending.setResolvedAt(Instant.now());
                    pending.setResolvedBatch(batch);
                });

        log.info("[batch/allocate] user {} enrolled into batch {}", user.getId(), batch.getId());
    }

    private void parkPending(User user, String trackCode, Long planId) {
        if (pendingBatchAllocationRepository.findByUserIdAndResolvedAtIsNull(user.getId()).isPresent()) {
            // V9's uq_one_open_pending_allocation already guarantees this can't happen twice —
            // defensive, not load-bearing.
            log.warn("[batch/allocate] user {} already has an open pending allocation, not creating a duplicate",
                    user.getId());
            return;
        }

        PendingBatchAllocation pending = new PendingBatchAllocation();
        pending.setUser(user);
        pending.setTrackCode(trackCode);
        pending.setPlanId(planId);
        pending.setReason("No ACTIVE/PLANNED batch matched track '" + trackCode + "' with free capacity.");
        pendingBatchAllocationRepository.save(pending);

        log.info("[batch/allocate] user {} parked pending — no batch available for track {}", user.getId(), trackCode);
        notifyPMs(user, trackCode);
    }

    private void notifyPMs(User user, String trackCode) {
        for (Long pmUserId : userService.findUserIdsByRole(RoleCode.TRAINER_PM)) {
            notificationService.enqueueAfterCommit(pmUserId, NotificationChannel.IN_APP, "BATCH_ALLOCATION_PENDING", Map.of(
                    "studentUuid", user.getUuid(),
                    "studentName", user.getFullName(),
                    "trackCode", trackCode));
        }
    }
}
