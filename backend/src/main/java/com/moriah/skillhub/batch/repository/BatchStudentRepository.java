package com.moriah.skillhub.batch.repository;

import com.moriah.skillhub.batch.dto.ActiveEnrollmentProjection;
import com.moriah.skillhub.batch.dto.ActiveMemberProjection;
import com.moriah.skillhub.batch.entity.BatchStudent;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BatchStudentRepository extends JpaRepository<BatchStudent, Long> {

    Optional<BatchStudent> findByBatchIdAndUserId(Long batchId, Long userId);

    /** Batch "which of these users have ever reached {@code status} in some batch" — one query
     * for a whole page of placement candidates ({@code PlacementService#candidateOptions}'s
     * graduated flag), instead of an {@code existsByUserIdAndStatus} per row. */
    @Query("SELECT DISTINCT bs.user.id FROM BatchStudent bs WHERE bs.status = :status AND bs.user.id IN :userIds")
    List<Long> findUserIdsByStatusAndUserIdIn(@Param("status") BatchStudentStatus status,
                                              @Param("userIds") Collection<Long> userIds);

    /** A student is only ever in one batch at a time in practice (one {@code ACTIVE}
     * subscription -> at most one live allocation) — used by de-allocation, which doesn't
     * already know the batch id. */
    Optional<BatchStudent> findByUserIdAndStatus(Long userId, BatchStudentStatus status);

    /** {@code BatchAllocationService#deallocate}'s lookup, widened to also find an {@code ON_PIP}
     * enrollment (feature 17 addendum, `/review` finding) — a subscription refunded while the
     * student is on an active PIP must still release their seat, not silently no-op and leave an
     * orphaned {@code ON_PIP} row with no subscription behind it. */
    Optional<BatchStudent> findByUserIdAndStatusIn(Long userId, List<BatchStudentStatus> statuses);

    Page<BatchStudent> findByBatchId(Long batchId, Pageable pageable);

    /** {@code GET /api/v1/batches/{id}/students} — the full roster, {@code user} fetched in the
     * same query (a small, bounded per-batch list, so no pagination; {@code @EntityGraph} not
     * {@code JOIN FETCH} keeps it composable and dodges the fetch-vs-pageable trap even though
     * there is no {@code Pageable} here). Ordered oldest-enrolment first for a stable UI. */
    @EntityGraph(attributePaths = "user")
    List<BatchStudent> findByBatchIdOrderByJoinedAtAscIdAsc(Long batchId);

    /** {@code BatchService#activeMembersOf}'s bulk read — see {@link ActiveMemberProjection}'s
     * Javadoc. One flat query across every batch a caller needs, not one lookup per batch.
     * Includes {@code ON_PIP} alongside {@code ACTIVE} (feature 17 addendum, `/review` finding):
     * this backs {@code AttendanceFinalisationJob}'s enrolled-student anti-join, and an ON_PIP
     * student who stops attending must still be auto-marked ABSENT — skipping them would inflate
     * their attendance_percent right when it matters most for their own PIP outcome. */
    @Query("SELECT new com.moriah.skillhub.batch.dto.ActiveMemberProjection(bs.batch.id, bs.user.id) " +
            "FROM BatchStudent bs WHERE bs.batch.id IN :batchIds AND bs.status IN ('ACTIVE', 'ON_PIP')")
    List<ActiveMemberProjection> findActiveMembers(@Param("batchIds") List<Long> batchIds);

    /** {@code BatchService#activeAndOnPipEnrollments}'s bulk read — see {@link
     * ActiveEnrollmentProjection}'s Javadoc. Unlike {@link #findActiveMembers}, this is not scoped
     * to a caller-supplied batch list: {@code StudentMetricsService} needs the whole platform's
     * live cohort every night, not one job's specific standups' batches. */
    @Query("SELECT new com.moriah.skillhub.batch.dto.ActiveEnrollmentProjection(bs.batch.id, bs.user.id, bs.joinedAt) " +
            "FROM BatchStudent bs WHERE bs.status IN ('ACTIVE', 'ON_PIP')")
    List<ActiveEnrollmentProjection> findActiveAndOnPipEnrollments();

    /** {@code BatchService#hasGraduated}'s backing check — feature 19's letter-eligibility rule
     * ("experience/relieving require GRADUATED or a clean EXITED — never a terminated student").
     * A student can graduate from at most one batch in practice, but this checks existence across
     * all of them rather than assuming that, since nothing enforces it at the schema level. */
    boolean existsByUserIdAndStatus(Long userId, BatchStudentStatus status);
}
