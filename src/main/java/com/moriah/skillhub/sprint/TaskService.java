package com.moriah.skillhub.sprint;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.project.ProjectService;
import com.moriah.skillhub.sprint.dto.AssignTaskRequest;
import com.moriah.skillhub.sprint.dto.CreateTaskRequest;
import com.moriah.skillhub.sprint.dto.TaskResponse;
import com.moriah.skillhub.sprint.dto.UpdateTaskRequest;
import com.moriah.skillhub.sprint.entity.Sprint;
import com.moriah.skillhub.sprint.entity.Task;
import com.moriah.skillhub.sprint.entity.TaskStatus;
import com.moriah.skillhub.sprint.repository.TaskRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * code-standards.md's own canonical example is {@code SprintService}; this is its sibling for
 * {@code Task}, following the identical shape. {@code BatchService} is injected for the same two
 * reasons {@code SprintService} injects it (ownership check, and — new here — the {@code
 * isActiveMember} eligibility check {@code pull}/{@code assign} need). {@code SprintService} is
 * injected only to reuse {@link SprintService#requireSprint} (same-package call, not a
 * cross-package one).
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    /** build-plan.md feature 11's state-machine diagram, made literal. The single source of
     * truth every mutation path (assign/pull/update) funnels through via {@link
     * #requireLegalTransition} — no path decides independently whether a move is legal.
     * <p>
     * {@code IN_REVIEW -> IN_PROGRESS} is a feature 12 addition, not in feature 11's original
     * diagram: {@code task_submissions}' own {@code (task_id, user_id, attempt_number)} unique
     * constraint presupposes a task can be resubmitted after review, which only makes sense if
     * {@code CHANGES_REQUESTED} sends it back to {@code IN_PROGRESS} rather than the terminal
     * {@code REJECTED} — see {@link #completeReview}. {@code REJECTED} stays reachable, but only
     * as a PM's direct, deliberate call via {@link #update} — {@code POST /reviews}'s verdict
     * path never produces it. */
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
            TaskStatus.BACKLOG, Set.of(TaskStatus.ASSIGNED),
            TaskStatus.ASSIGNED, Set.of(TaskStatus.IN_PROGRESS),
            TaskStatus.IN_PROGRESS, Set.of(TaskStatus.IN_REVIEW),
            TaskStatus.IN_REVIEW, Set.of(TaskStatus.COMPLETED, TaskStatus.REJECTED, TaskStatus.IN_PROGRESS),
            TaskStatus.COMPLETED, Set.of(),
            TaskStatus.REJECTED, Set.of());

    private final TaskRepository taskRepository;
    private final SprintService sprintService;
    private final BatchService batchService;
    private final UserRepository userRepository;
    private final TaskPullGuard taskPullGuard;
    private final ProjectService projectService;

    @Transactional
    public TaskResponse create(Long callerUserId, CreateTaskRequest request) {
        Sprint sprint = sprintService.requireSprint(request.sprintId());
        batchService.requireOwnerOrAdmin(callerUserId, sprint.getBatch());
        // build-plan.md feature 15 verify line: "A DRAFT cannot attach to a task."
        if (request.projectId() != null) {
            projectService.requirePublished(request.projectId());
        }

        Task task = new Task();
        task.setSprint(sprint);
        task.setProjectId(request.projectId());
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setTaskType(request.taskType());
        task.setStoryPoints(request.storyPoints());
        task.setDueAt(request.dueAt());
        task.setStatus(TaskStatus.BACKLOG);
        taskRepository.save(task);

        return toResponse(task);
    }

    /** {@code sprintId} required (see {@code TaskRepository.search}'s Javadoc); {@code status}/
     * {@code assignedToUuid} optional. */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(Long sprintId, TaskStatus status, String assignedToUuid, Pageable pageable) {
        Long assignedToId = assignedToUuid == null ? null : requireUserByUuid(assignedToUuid).getId();
        return PageResponse.from(taskRepository.search(sprintId, status, assignedToId, pageable)
                .map(this::toResponse));
    }

    /** {@code sprintId} is immutable here (see {@code UpdateTaskRequest}'s Javadoc); {@code
     * status} moves the state machine forward except {@code BACKLOG -> ASSIGNED}, which is
     * reserved for {@link #assign}/{@link #pull} (their eligibility checks don't run here). */
    @Transactional
    public TaskResponse update(Long callerUserId, Long taskId, UpdateTaskRequest request) {
        Task task = requireTask(taskId);
        batchService.requireOwnerOrAdmin(callerUserId, task.getSprint().getBatch());

        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setTaskType(request.taskType());
        task.setStoryPoints(request.storyPoints());
        task.setDueAt(request.dueAt());

        TaskStatus current = task.getStatus();
        TaskStatus requested = request.status();
        if (requested != current) {
            if (current == TaskStatus.BACKLOG && requested == TaskStatus.ASSIGNED) {
                throw new BusinessException(ErrorCode.TASK_INVALID_TRANSITION,
                        "Use POST /tasks/{id}/assign or /tasks/{id}/pull to move a task out of BACKLOG.");
            }
            requireLegalTransition(current, requested);
            task.setStatus(requested);
            if (requested == TaskStatus.COMPLETED) {
                completeTask(task);
            }
        }

        return toResponse(task);
    }

    /** PM/Admin-driven assignment. build-plan.md feature 11 "Sensible defaults": "PM/Admin-gated
     * ... must check the student is actually an ACTIVE member of the task's sprint's batch" —
     * applied here too, not only on {@link #pull}, for the same data-integrity reason.
     * <p>
     * Deliberately does NOT call {@code taskPullGuard.blocksPull} (feature 17 addendum, `/review`
     * considered and rejected extending the block here) — build-plan.md's own wording ties the
     * block specifically to "a student self-assigns" (unsupervised self-service pulling more work
     * onto an already-overdue plate), not to a PM's own deliberate, supervised assignment. A PM
     * assigning a specific task to help a PROJECT_DELAY-flagged student catch up is exactly the
     * kind of informed override the block isn't meant to prevent. */
    @Transactional
    public TaskResponse assign(Long callerUserId, Long taskId, AssignTaskRequest request) {
        Task task = requireTask(taskId);
        Batch batch = task.getSprint().getBatch();
        batchService.requireOwnerOrAdmin(callerUserId, batch);

        User student = requireUserByUuid(request.userUuid());
        if (!batchService.isActiveMember(batch.getId(), student.getId())) {
            throw new ForbiddenOperationException(ErrorCode.NOT_BATCH_MEMBER);
        }

        requireLegalTransition(task.getStatus(), TaskStatus.ASSIGNED);
        task.setAssignedTo(student);
        task.setStatus(TaskStatus.ASSIGNED);

        return toResponse(task);
    }

    /** Student self-service. build-plan.md feature 11: "a student self-assigns a BACKLOG task ...
     * blocked if an open pip_records row has blocks_task_pull = true." Order: batch-membership
     * (403 — the caller isn't even eligible to touch this batch's tasks), then transition
     * legality (409 — the task isn't in a pullable state), then the PIP hook (409 — eligible and
     * legal, but currently restricted). */
    @Transactional
    public TaskResponse pull(Long callerUserId, Long taskId) {
        Task task = requireTask(taskId);
        Long batchId = task.getSprint().getBatch().getId();

        if (!batchService.isActiveMember(batchId, callerUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_BATCH_MEMBER);
        }

        requireLegalTransition(task.getStatus(), TaskStatus.ASSIGNED);

        if (taskPullGuard.blocksPull(callerUserId)) {
            throw new BusinessException(ErrorCode.TASK_PULL_BLOCKED_BY_PIP);
        }

        User student = requireUserById(callerUserId);
        task.setAssignedTo(student);
        task.setStatus(TaskStatus.ASSIGNED);

        return toResponse(task);
    }

    /** Backs {@code GET /api/v1/reviews/queue} (feature 12) — {@code IN_REVIEW} tasks, scoped to
     * the caller's own batches unless {@code ADMIN} (same {@code SecurityUtils.currentUserRoles}
     * technique {@code BatchService.requireOwnerOrAdmin} already uses, no DB round trip). Lives
     * here, not in {@code submission/}, since it's a query over {@code Task} — {@code
     * submission/ReviewController} calls this cross-package method directly. */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> reviewQueue(Long callerUserId, Pageable pageable) {
        boolean isAdmin = SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name());
        Long pmId = isAdmin ? null : callerUserId;
        return PageResponse.from(taskRepository.findReviewQueue(TaskStatus.IN_REVIEW, pmId, pageable)
                .map(this::toResponse));
    }

    /** Called by {@code submission/SubmissionService} when a student's PR verifies — the caller
     * is the student, not the batch PM, so authorization here is "you are this task's own {@code
     * assignedTo}", not {@code requireOwnerOrAdmin}. Cross-package call to this service's public
     * method, never {@code TaskRepository} directly (architecture.md layer-boundary rule).
     * <p>
     * Idempotent when the task is already {@code IN_REVIEW} — unlike {@code SprintService
     * .activate}'s deliberate strictness, a duplicate call here <em>is</em> the scenario this
     * method exists to handle (build-plan.md feature 12: "double-POST does not create two
     * submissions" — the second, racing {@code create()} call still needs to land here safely). */
    @Transactional
    public void markInReview(Long taskId, Long studentUserId) {
        Task task = requireTask(taskId);
        User assignee = task.getAssignedTo();
        if (assignee == null || !assignee.getId().equals(studentUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (task.getStatus() == TaskStatus.IN_REVIEW) {
            return;
        }
        requireLegalTransition(task.getStatus(), TaskStatus.IN_REVIEW);
        task.setStatus(TaskStatus.IN_REVIEW);
    }

    /** Called by {@code submission/ReviewService} after a PM posts a code review verdict —
     * {@code outcome} is {@code COMPLETED} for {@code APPROVED} or {@code IN_PROGRESS} for
     * {@code CHANGES_REQUESTED} (see {@code ALLOWED_TRANSITIONS}' Javadoc for why it's not
     * {@code REJECTED}); {@code submission/} owns that verdict-to-status mapping, not this class.
     * Reuses the same PM/Admin ownership check {@link #update} enforces — the reviewer already
     * is the batch PM in the normal case. */
    @Transactional
    public void completeReview(Long taskId, Long callerUserId, TaskStatus outcome) {
        Task task = requireTask(taskId);
        batchService.requireOwnerOrAdmin(callerUserId, task.getSprint().getBatch());

        requireLegalTransition(task.getStatus(), outcome);
        task.setStatus(outcome);
        if (outcome == TaskStatus.COMPLETED) {
            completeTask(task);
        }
    }

    /** {@code UserService#getPortfolio}'s {@code completedProjects} field — a cross-package
     * service call, never {@code TaskRepository} directly. Returns bare project ids; {@code
     * UserService} resolves them to titles/slugs via {@code project.ProjectService}, which owns
     * that mapping (this class has no business reading {@code Project} rows itself). */
    @Transactional(readOnly = true)
    public List<Long> completedProjectIdsFor(Long userId) {
        return taskRepository.findDistinctCompletedProjectIds(userId);
    }

    private void requireLegalTransition(TaskStatus from, TaskStatus to) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(ErrorCode.TASK_INVALID_TRANSITION,
                    "Cannot move a task from %s to %s.".formatted(from, to));
        }
    }

    /** build-plan.md feature 11: "completed_points recalculated on completion" — rolled up onto
     * the sprint. {@code sprint} is already a managed entity in this transaction (loaded via
     * {@code task.getSprint()}), so the mutation is picked up by Hibernate's dirty checking on
     * commit — no explicit {@code save()}, same as {@code BatchService.update}'s treatment of its
     * own managed {@code Batch}. A task with no {@code storyPoints} contributes zero. */
    private void completeTask(Task task) {
        task.setCompletedAt(Instant.now());
        Sprint sprint = task.getSprint();
        int points = task.getStoryPoints() != null ? task.getStoryPoints() : 0;
        sprint.setCompletedPoints(sprint.getCompletedPoints() + points);
    }

    private Task requireTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TASK_NOT_FOUND, taskId));
    }

    private User requireUserByUuid(String uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, uuid));
    }

    private User requireUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
    }

    private TaskResponse toResponse(Task task) {
        User assignee = task.getAssignedTo();
        return new TaskResponse(
                task.getId(),
                task.getSprint().getId(),
                task.getProjectId(),
                task.getTitle(),
                task.getDescription(),
                task.getTaskType(),
                assignee != null ? assignee.getUuid() : null,
                assignee != null ? assignee.getFullName() : null,
                task.getStoryPoints(),
                task.getDueAt(),
                task.getStatus(),
                task.getCompletedAt());
    }
}
