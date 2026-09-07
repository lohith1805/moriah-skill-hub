package com.moriah.skillhub.batch.repository;

import com.moriah.skillhub.batch.entity.PendingBatchAllocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PendingBatchAllocationRepository extends JpaRepository<PendingBatchAllocation, Long> {

    /** V9's {@code uq_one_open_pending_allocation} generated column guarantees at most one
     * unresolved row per user — same pattern as {@code UserSubscriptionRepository}'s active-
     * subscription lookup. */
    Optional<PendingBatchAllocation> findByUserIdAndResolvedAtIsNull(Long userId);

    /** The whole "assignment pending" queue — {@code GET /api/v1/batches/pending-allocations} for
     * a PM screen. {@code user} join-fetched so the response can carry uuid/name/email. */
    @EntityGraph(attributePaths = "user")
    List<PendingBatchAllocation> findByResolvedAtIsNullOrderByCreatedAtAsc();

    /** Just the ones for one track — used to drain the queue when a new batch for that track is
     * created (oldest first, so a first-come order is honoured while capacity lasts). */
    @EntityGraph(attributePaths = "user")
    List<PendingBatchAllocation> findByResolvedAtIsNullAndTrackCodeOrderByCreatedAtAsc(String trackCode);
}
