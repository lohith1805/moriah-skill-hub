package com.moriah.skillhub.batch.repository;

import com.moriah.skillhub.batch.entity.PendingBatchAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingBatchAllocationRepository extends JpaRepository<PendingBatchAllocation, Long> {

    /** V9's {@code uq_one_open_pending_allocation} generated column guarantees at most one
     * unresolved row per user — same pattern as {@code UserSubscriptionRepository}'s active-
     * subscription lookup. */
    Optional<PendingBatchAllocation> findByUserIdAndResolvedAtIsNull(Long userId);
}
