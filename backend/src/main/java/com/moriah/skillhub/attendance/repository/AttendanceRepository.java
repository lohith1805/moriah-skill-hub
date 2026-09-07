package com.moriah.skillhub.attendance.repository;

import com.moriah.skillhub.attendance.dto.AttendanceKeyProjection;
import com.moriah.skillhub.attendance.entity.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /** {@code JOIN FETCH} on {@code standup}/{@code user}, {@code LEFT JOIN FETCH} on {@code
     * markedBy} (nullable — an inner join would silently exclude every self-check-in row that has
     * no PM override), deliberately, not the derived-query default: this backs {@code
     * AttendanceWriter.findExisting} (the concurrent double check-in recovery read, run in its own
     * short-lived {@code REQUIRES_NEW} transaction/session) as well as {@code
     * AttendanceService#override}'s existing-row lookup. Without eager fetch, the returned
     * entity's associations stay lazy proxies — for the recovery-read path, proxies tied to an
     * already-closed session, causing {@code LazyInitializationException} the instant {@code
     * toResponse} reads them (confirmed the hard way for the identical shape of bug in {@code
     * TaskSubmissionRepository}, feature 12 — missing {@code markedBy} here reproduces it: a
     * student checking in to a standup a PM already overrode recovers that PM-owned row via this
     * query, and {@code toResponse} reads {@code markedBy.getUuid()}). */
    @Query("SELECT a FROM Attendance a JOIN FETCH a.user JOIN FETCH a.standup LEFT JOIN FETCH a.markedBy " +
            "WHERE a.standup.id = :standupId AND a.user.id = :userId")
    Optional<Attendance> findByStandupIdAndUserId(@Param("standupId") Long standupId, @Param("userId") Long userId);

    /** {@code GET /api/v1/attendance/me} — {@code @EntityGraph}, not {@code JOIN FETCH}: this is
     * paginated (same reasoning as {@code TaskRepository.search}). {@code standup.batch} is
     * deliberately NOT included — {@code AttendanceResponse.batchId} only ever reads {@code
     * standup.getBatch().getId()}, and {@code .getId()} on a lazy {@code @ManyToOne} proxy never
     * hits the DB (same reasoning {@code TaskResponse.sprintId} relies on, no entity graph
     * needed). {@code markedBy} is included — nullable, but non-null on every PM-overridden row,
     * and {@code toResponse} reads it unconditionally when present. */
    @EntityGraph(attributePaths = {"standup", "user", "markedBy"})
    Page<Attendance> findByUserId(Long userId, Pageable pageable);

    /** {@code GET /api/v1/attendance/batch/{batchId}} — {@code attendance} has no direct {@code
     * batch_id} column (V7 schema), so the {@code WHERE} joins through {@code standup.batch}; the
     * {@code @EntityGraph} itself omits {@code standup.batch} for the same "only {@code .getId()}
     * is read" reasoning as {@link #findByUserId}. */
    @EntityGraph(attributePaths = {"standup", "user", "markedBy"})
    @Query("SELECT a FROM Attendance a WHERE a.standup.batch.id = :batchId ORDER BY a.standup.scheduledAt DESC")
    Page<Attendance> findByBatch(@Param("batchId") Long batchId, Pageable pageable);

    /** {@code AttendanceFinalisationJob}'s bulk "who already has a row" read — one flat query
     * across every eligible standup in this run, not one lookup per standup. See {@link
     * AttendanceKeyProjection}'s Javadoc. */
    @Query("SELECT new com.moriah.skillhub.attendance.dto.AttendanceKeyProjection(a.standup.id, a.user.id) " +
            "FROM Attendance a WHERE a.standup.id IN :standupIds")
    List<AttendanceKeyProjection> findKeysByStandupIds(@Param("standupIds") List<Long> standupIds);
}
