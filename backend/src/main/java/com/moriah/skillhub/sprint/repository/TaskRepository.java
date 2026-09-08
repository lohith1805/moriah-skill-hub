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

import java.util.Collection;
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

    /** Backs {@code GET /api/v1/tasks/me} — a STUDENT's whole board in one query instead of the
     * old client fan-out ({@code GET /batches} -> {@code GET /sprints?batchId=} -> {@code GET
     * /tasks?sprintId=} per sprint). {@code batchIds} is the caller's live enrolment, resolved by
     * {@code BatchService#activeBatchIdsForUser} (never a {@code BatchStudent} read here — same
     * boundary {@code findReviewQueue}'s {@code b.pm.id} filter respects). Rows: tasks assigned to
     * the caller, plus still-pullable {@code BACKLOG} tasks anywhere in those batches. {@code
     * @EntityGraph} on {@code assignedTo}, not {@code JOIN FETCH}, for the same paginate-safety
     * reason {@link #search}/{@link #findReviewQueue} document. */
    @EntityGraph(attributePaths = "assignedTo")
    @Query("""
            SELECT t FROM Task t
             JOIN t.sprint s
             WHERE s.batch.id IN :batchIds
               AND (t.assignedTo.id = :userId OR t.status = 'BACKLOG')
             ORDER BY t.dueAt ASC, t.id ASC
            """)
    Page<Task> findMyBoard(@Param("batchIds") Collection<Long> batchIds, @Param("userId") Long userId, Pageable pageable);

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

    /** {@code pip/PipService#progress} — the still-open tasks assigned to a student on a PIP, so
     * the recovery-progress panel can list "what you still owe" (empty for e.g. an attendance PIP
     * where the student's task board is already clear). Read directly from {@code pip} rather than
     * through {@code sprint/TaskService} to avoid a {@code TaskService -> TaskPullGuard ->
     * PipService -> TaskService} bean cycle — the same shared-kernel pragmatism {@code
     * SprintService} uses to read {@code BatchRepository} directly. */
    @EntityGraph(attributePaths = "assignedTo")
    List<Task> findByAssignedToIdAndStatusInOrderByDueAtAsc(Long assignedToId, Collection<TaskStatus> statuses);

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
