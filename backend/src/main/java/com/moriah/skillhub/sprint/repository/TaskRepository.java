package com.moriah.skillhub.sprint.repository;

import com.moriah.skillhub.sprint.dto.SprintTaskStatusCount;
import com.moriah.skillhub.sprint.entity.Task;
import com.moriah.skillhub.sprint.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    /** {@code TaskService#completedProjectIdsFor}'s backing query — the Open Stub cleared this
     * feature: {@code UserService#getPortfolio}'s {@code completedProjects}, defined as "at least
     * one {@code COMPLETED} task assigned to this student under that project" (build-plan.md
     * feature 20 decision — there is no per-student "project completion" table in this schema).
     * {@code DISTINCT} because a student can complete more than one task under the same project. */
    @Query("""
            SELECT DISTINCT t.projectId FROM Task t
             WHERE t.assignedTo.id = :userId AND t.status = 'COMPLETED' AND t.projectId IS NOT NULL
            """)
    List<Long> findDistinctCompletedProjectIds(@Param("userId") Long userId);

    /** {@code SprintService#taskStatusCountsForBatch}'s backing query — FRS MSH-FR-PM-02/
     * MSH-FR-BA-03: task-level progress (counts per status), not just the story-point rollup
     * {@code SprintProgressProjection} already carries. Aggregate counts only — no task title,
     * assignee, or due date leaves this query, same privacy boundary {@code
     * ClientProjectProgressResponse}'s own Javadoc already establishes for burndown data. */
    @Query("""
            SELECT new com.moriah.skillhub.sprint.dto.SprintTaskStatusCount(
                t.sprint.id, t.status, COUNT(t))
            FROM Task t
            WHERE t.sprint.batch.id = :batchId
            GROUP BY t.sprint.id, t.status
            """)
    List<SprintTaskStatusCount> countTaskStatusesForBatch(@Param("batchId") Long batchId);
}
