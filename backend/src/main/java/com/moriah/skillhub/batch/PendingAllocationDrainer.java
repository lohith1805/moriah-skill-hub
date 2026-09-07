package com.moriah.skillhub.batch;

import com.moriah.skillhub.batch.repository.PendingBatchAllocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * When a batch for some track is created, re-runs allocation for every student still parked in
 * {@code pending_batch_allocations} for that track — oldest request first, so first-come order is
 * honoured while the new batch's capacity lasts. Each {@link BatchAllocationService#allocate}
 * call is its own transaction (cross-bean, so the proxy applies), so one row that fails is logged
 * and skipped without touching the others or the batch-creation that triggered this.
 *
 * <p>Runs {@code AFTER_COMMIT} (the new batch must be visible to the candidate query) and
 * {@code @Async} (the {@code POST /batches} response shouldn't block on the drain) — the same
 * shape as {@code InvoiceGenerationJob}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingAllocationDrainer {

    private final PendingBatchAllocationRepository pendingBatchAllocationRepository;
    private final BatchAllocationService batchAllocationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBatchCreated(BatchCreatedEvent event) {
        pendingBatchAllocationRepository
                .findByResolvedAtIsNullAndTrackCodeOrderByCreatedAtAsc(event.trackCode())
                .forEach(row -> {
                    try {
                        batchAllocationService.allocate(
                                row.getUser().getId(), row.getTrackCode(), row.getPlanId());
                    } catch (RuntimeException e) {
                        log.error("[batch/allocate] pending retry failed for user {} on track {}",
                                row.getUser().getId(), event.trackCode(), e);
                    }
                });
    }
}
