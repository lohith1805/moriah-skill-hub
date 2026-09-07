package com.moriah.skillhub.client.repository;

import com.moriah.skillhub.client.entity.BaMeeting;
import com.moriah.skillhub.client.entity.BaMeetingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BaMeetingRepository extends JpaRepository<BaMeeting, Long> {

    /** {@code GET /api/v1/ba/meetings} — both filters optional ({@code null} drops the predicate,
     * the {@code UserRepository.search} idiom). {@code clientProject} eager so the response
     * builds without a lazy round trip per row. */
    @Query("""
            SELECT m FROM BaMeeting m
             WHERE (:status IS NULL OR m.status = :status)
               AND (:clientProjectId IS NULL OR m.clientProject.id = :clientProjectId)
            """)
    @EntityGraph(attributePaths = "clientProject")
    Page<BaMeeting> search(@Param("status") BaMeetingStatus status,
                           @Param("clientProjectId") Long clientProjectId,
                           Pageable pageable);

    @EntityGraph(attributePaths = "clientProject")
    Optional<BaMeeting> findWithProjectById(Long id);

    /** {@code BaMeetingService#myMeetings} — "Client Pre-Project Discussions" on an attendee's own
     * dashboard: every meeting they're invited to, regardless of who scheduled it. */
    @EntityGraph(attributePaths = "clientProject")
    Page<BaMeeting> findByIdIn(Collection<Long> ids, Pageable pageable);

    /** {@code BaMeetingAutoCompleteService} — every still-{@code SCHEDULED} meeting, checked
     * in-memory against its own {@code scheduledAt + durationMinutes} (varies per row, so it
     * can't be pushed into a single {@code WHERE} cutoff) — same idiom {@code
     * QuizAttemptExpiryService#expire} already establishes for {@code QuizAttempt}. */
    List<BaMeeting> findByStatus(BaMeetingStatus status);
}
