package com.moriah.skillhub.sprint.repository;

import com.moriah.skillhub.sprint.dto.SprintProgressProjection;
import com.moriah.skillhub.sprint.dto.VelocityProjection;
import com.moriah.skillhub.sprint.entity.Sprint;
import com.moriah.skillhub.sprint.entity.SprintStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    boolean existsByBatchIdAndSprintNumber(Long batchId, Integer sprintNumber);

    Optional<Sprint> findByBatchIdAndSprintNumber(Long batchId, Integer sprintNumber);

    /** "One ACTIVE sprint per batch" (build-plan.md feature 11) — used by
     * {@code SprintService.transitionStatus} to enforce it. */
    Optional<Sprint> findByBatchIdAndStatus(Long batchId, SprintStatus status);

    /** {@code SprintService#allSprintsClosed}'s backing check (build-plan.md feature 20's
     * certificate-issuance eligibility gate: "all sprints closed") — "any sprint in this batch
     * that is NOT COMPLETED", so a batch is eligible only when this returns {@code false}. A batch
     * with zero sprints returns {@code false} here too (vacuously — nothing exists that isn't
     * COMPLETED), which is the deliberate, unguarded pass-through {@code allSprintsClosed}'s own
     * Javadoc documents. */
    boolean existsByBatchIdAndStatusNot(Long batchId, SprintStatus status);

    Page<Sprint> findByBatchIdOrderBySprintNumberAsc(Long batchId, Pageable pageable);

    /** "Non-overlapping within a batch" (build-plan.md feature 11). {@code excludeId} is null on
     * create (nothing to exclude yet) and the sprint's own id on update (a sprint doesn't overlap
     * itself). */
    @Query("""
            SELECT COUNT(s) > 0 FROM Sprint s
             WHERE s.batch.id = :batchId
               AND (:excludeId IS NULL OR s.id <> :excludeId)
               AND s.startDate <= :endDate AND s.endDate >= :startDate
            """)
    boolean existsOverlapping(@Param("batchId") Long batchId, @Param("excludeId") Long excludeId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** code-standards.md's own canonical example, verbatim — "Velocity = sum of completed_points
     * across a batch's COMPLETED sprints" (build-plan.md feature 11 "Sensible defaults"), a
     * derived-record projection rather than loading full entities to compute a number
     * (code-standards.md "Repository": "Always project into a record for read-only aggregates"). */
    @Query("""
            SELECT new com.moriah.skillhub.sprint.dto.VelocityProjection(
                s.id, s.sprintNumber, s.plannedPoints, s.completedPoints)
            FROM Sprint s
            WHERE s.batch.id = :batchId AND s.status = 'COMPLETED'
            """)
    List<VelocityProjection> findVelocity(@Param("batchId") Long batchId);

    /** feature 21 (BA and Client Portal): {@code SprintService#progressForBatch}'s backing
     * query — every sprint for a batch, not just {@code COMPLETED} ones (unlike {@link
     * #findVelocity}), since {@code ClientProjectService#progress} needs both the per-sprint
     * burndown and the batch-wide milestone-completion fraction from one call. Same
     * derived-record-projection style. */
    @Query("""
            SELECT new com.moriah.skillhub.sprint.dto.SprintProgressProjection(
                s.id, s.sprintNumber, s.status, s.plannedPoints, s.completedPoints)
            FROM Sprint s
            WHERE s.batch.id = :batchId
            ORDER BY s.sprintNumber ASC
            """)
    List<SprintProgressProjection> findProgressForBatch(@Param("batchId") Long batchId);
}
