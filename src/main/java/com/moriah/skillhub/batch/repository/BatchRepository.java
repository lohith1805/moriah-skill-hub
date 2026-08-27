package com.moriah.skillhub.batch.repository;

import com.moriah.skillhub.batch.entity.Batch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.List;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    /** Overrides the inherited {@code findAll(Pageable)} with an {@code @EntityGraph} —
     * {@code BatchService.list} reads {@code pm} on every row for {@code BatchResponse}; without
     * this it's an N+1 (code-standards.md "N+1 Prevention"). {@code @EntityGraph}, not {@code
     * JOIN FETCH}, because this is paginated (library-docs.md: "JOIN FETCH with Pageable causes
     * in-memory pagination"). */
    @Override
    @EntityGraph(attributePaths = "pm")
    @NonNull
    Page<Batch> findAll(@NonNull Pageable pageable);

    /**
     * `/architect feature 10`: native SQL, not JPQL — {@code Batch.planTierMinId} is a bare
     * {@code Long} (cross-package reasoning, see {@code Batch}'s Javadoc), so matching it against
     * a tier rank needs a join into {@code subscription_plans}. Same technique {@code
     * EntitlementFlagsLoader} already uses to read across a package boundary without importing
     * {@code subscription/}'s repository or entity classes. {@code enrolled_count < capacity} is
     * a candidate pre-filter only — the actual capacity guarantee is {@link #tryReserveSeat}'s
     * atomic update, called per candidate in the order returned here.
     */
    @Query(value = """
            SELECT b.* FROM batches b
            LEFT JOIN subscription_plans sp ON sp.id = b.plan_tier_min_id
             WHERE b.status IN ('PLANNED', 'ACTIVE')
               AND b.track_code = :trackCode
               AND (b.plan_tier_min_id IS NULL OR sp.tier_rank <= :tierRank)
               AND b.enrolled_count < b.capacity
             ORDER BY b.start_date ASC, b.id ASC
            """, nativeQuery = true)
    List<Batch> findAllocationCandidates(@Param("trackCode") String trackCode, @Param("tierRank") int tierRank);

    /** code-standards.md "Transactions": a conditional atomic update, not {@code @Version} —
     * race-free under concurrent allocation with no retry loop. Returns 0 if the batch is full
     * (raced full since {@link #findAllocationCandidates} was read) or doesn't exist. */
    @Modifying
    @Query(value = "UPDATE batches SET enrolled_count = enrolled_count + 1 WHERE id = :batchId AND enrolled_count < capacity",
            nativeQuery = true)
    int tryReserveSeat(@Param("batchId") Long batchId);

    /** The de-allocation counterpart — removal/refund never goes below zero even if called
     * twice for the same student (defensive; the caller is expected to check status first). */
    @Modifying
    @Query(value = "UPDATE batches SET enrolled_count = enrolled_count - 1 WHERE id = :batchId AND enrolled_count > 0",
            nativeQuery = true)
    int releaseSeat(@Param("batchId") Long batchId);
}
