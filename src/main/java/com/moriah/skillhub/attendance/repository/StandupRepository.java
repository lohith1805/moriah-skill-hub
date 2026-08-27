package com.moriah.skillhub.attendance.repository;

import com.moriah.skillhub.attendance.entity.Standup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StandupRepository extends JpaRepository<Standup, Long> {

    /** Backs {@code GET /api/v1/standups?batchId=&date=} — {@code batchId} required (mirrors
     * {@code SprintRepository}'s own {@code GET /sprints?batchId=} precedent), {@code
     * startAt}/{@code endAt} are an optional day-range computed by {@code StandupService} from
     * the {@code date} query param (never a raw {@code DATE()} comparison — {@code scheduled_at}
     * is a precise instant, not a date column). No {@code @EntityGraph}: {@code StandupResponse}
     * reads only scalar fields plus {@code batch.getId()} — a lazy {@code @ManyToOne} proxy
     * returns its id without hitting the DB, so nothing here needs eager fetching. */
    @Query("""
            SELECT s FROM Standup s
             WHERE s.batch.id = :batchId
               AND (:startAt IS NULL OR s.scheduledAt >= :startAt)
               AND (:endAt IS NULL OR s.scheduledAt < :endAt)
             ORDER BY s.scheduledAt DESC
            """)
    Page<Standup> search(@Param("batchId") Long batchId, @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt, Pageable pageable);

    /** {@code AttendanceFinalisationJob}'s whole cohort in one flat query (code-standards.md
     * "N+1 Prevention" / AGENTS.md "a repository call inside the per-item loop is a defect") —
     * every non-cancelled, not-yet-finalised standup whose {@code scheduledAt} has passed. A
     * still-{@code SCHEDULED} row in this set is exactly the one the job auto-conducts before
     * finalising it (`/architect feature 13` decision); a {@code CONDUCTED} row is one a PM's
     * students already checked into during the day and just needs its gaps filled. */
    @Query("""
            SELECT s FROM Standup s
             WHERE s.status <> 'CANCELLED'
               AND s.finalisedAt IS NULL
               AND s.scheduledAt < :now
            """)
    List<Standup> findEligibleForFinalisation(@Param("now") Instant now);
}
