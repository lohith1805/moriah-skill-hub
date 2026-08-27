package com.moriah.skillhub.sprint.repository;

import com.moriah.skillhub.sprint.entity.Task;
import com.moriah.skillhub.sprint.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Backs {@code GET /api/v1/tasks?sprintId=&status=&assignedTo=} — {@code sprintId} is
     * required (mirrors code-standards.md's own {@code GET /sprints?batchId=} example: a required
     * scope parameter, not an unbounded cross-sprint scan), {@code status}/{@code assignedToId}
     * are optional filters. {@code @EntityGraph} on {@code assignedTo} — {@code TaskService}
     * reads {@code assignedTo.uuid}/{@code .fullName} for every row in {@code TaskResponse}
     * (matches {@code BatchResponse}'s {@code pmUuid}/{@code pmFullName}); without it that's an
     * N+1 (code-standards.md "N+1 Prevention"). {@code @EntityGraph}, not {@code JOIN FETCH},
     * because this is paginated (same reasoning as {@code BatchRepository.findAll}).
     */
    @EntityGraph(attributePaths = "assignedTo")
    @Query("""
            SELECT t FROM Task t
             WHERE t.sprint.id = :sprintId
               AND (:status IS NULL OR t.status = :status)
               AND (:assignedToId IS NULL OR t.assignedTo.id = :assignedToId)
            """)
    Page<Task> search(@Param("sprintId") Long sprintId, @Param("status") TaskStatus status,
            @Param("assignedToId") Long assignedToId, Pageable pageable);

    /** Backs {@code GET /api/v1/reviews/queue} (feature 12) — {@code pmId} is {@code null} for
     * {@code ADMIN} (sees every batch's queue), or the caller's id for a {@code TRAINER_PM}
     * (scoped to batches they own, same ownership model {@code BatchService} established). A
     * plain {@code JOIN} for the filter condition, not {@code JOIN FETCH} — that would hit the
     * same "in-memory pagination" problem {@code BatchRepository.findAll}'s own Javadoc
     * documents; {@code @EntityGraph} on {@code assignedTo} is the paginate-safe way to avoid the
     * N+1 {@code toResponse} would otherwise cause. */
    @EntityGraph(attributePaths = "assignedTo")
    @Query("""
            SELECT t FROM Task t
             JOIN t.sprint s
             JOIN s.batch b
             WHERE t.status = :status
               AND (:pmId IS NULL OR b.pm.id = :pmId)
             ORDER BY t.dueAt ASC
            """)
    Page<Task> findReviewQueue(@Param("status") TaskStatus status, @Param("pmId") Long pmId, Pageable pageable);
}
