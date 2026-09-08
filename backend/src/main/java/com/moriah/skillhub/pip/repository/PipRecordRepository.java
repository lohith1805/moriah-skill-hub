package com.moriah.skillhub.pip.repository;

import com.moriah.skillhub.pip.entity.PipRecord;
import com.moriah.skillhub.pip.entity.PipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PipRecordRepository extends JpaRepository<PipRecord, Long> {

    /** {@code PipEvaluationService}'s "one open record per user" pre-filter — one flat query for
     * every user already flagged, so the per-student evaluation loop never needs a repository call
     * to check (AGENTS.md: "a repository call inside the per-student loop is a defect"). Filters
     * on {@code openUserId IS NOT NULL} rather than {@code status IN (...)} specifically so this
     * query can use {@code uq_one_open_pip}'s unique index directly — a status-only filter has no
     * supporting index and would force a full scan of every historical {@code pip_records} row
     * (`/review` finding). */
    @Query("SELECT p.user.id FROM PipRecord p WHERE p.openUserId IS NOT NULL")
    List<Long> findOpenUserIds();

    /** {@code GET /api/v1/pip/me} — a student's own currently-open record, if any. */
    @EntityGraph(attributePaths = {"user", "batch"})
    Optional<PipRecord> findByUserIdAndStatusIn(Long userId, List<PipStatus> statuses);

    /** Backs {@code GET /api/v1/pip?batchId=&status=} — both filters optional, matching {@code
     * ProjectRepository#search}'s exact shape. */
    @EntityGraph(attributePaths = {"user", "batch"})
    @Query("""
            SELECT p FROM PipRecord p
             WHERE (:batchId IS NULL OR p.batch.id = :batchId)
               AND (:status IS NULL OR p.status = :status)
             ORDER BY p.triggeredAt DESC
            """)
    Page<PipRecord> search(@Param("batchId") Long batchId, @Param("status") PipStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "batch"})
    Optional<PipRecord> findById(Long id);

    /** {@code PipEvaluationService#autoResolveElapsed} — every still-open record whose 15-day
     * window has fully elapsed ({@code endDate} strictly before today). Bounded to cohort members
     * currently on a PIP, so no pagination. */
    @EntityGraph(attributePaths = {"user", "batch"})
    List<PipRecord> findByStatusInAndEndDateBefore(List<PipStatus> statuses, LocalDate date);

    /** {@code PipService#blocksPull}'s check, called from {@code sprint/TaskPullGuard} on every
     * {@code POST /tasks/{id}/pull} — the hottest path this feature has, so it needs a genuinely
     * indexed lookup, not a scan (see V11's {@code idx_pip_records_pull_block} comment). */
    boolean existsByUserIdAndStatusInAndBlocksTaskPullTrue(Long userId, List<PipStatus> statuses);

    /** {@code PipService#hasOpenPip}'s check for {@code CertificateService#issue}'s eligibility
     * gate (build-plan.md feature 20: "no open pip_records row"). Filters on {@code openUserId}
     * directly rather than {@code userId} + {@code status IN (...)} — same reasoning as {@link
     * #findOpenUserIds}'s own Javadoc: this is the column {@code uq_one_open_pip} actually indexes,
     * so a point lookup against it is genuinely indexed instead of an unindexed status scan. */
    boolean existsByOpenUserId(Long userId);
}
