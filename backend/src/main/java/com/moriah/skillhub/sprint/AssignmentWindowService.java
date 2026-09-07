package com.moriah.skillhub.sprint;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.sprint.dto.AssignmentWindowResponse;
import com.moriah.skillhub.sprint.dto.CreateAssignmentWindowRequest;
import com.moriah.skillhub.sprint.entity.AssignmentWindow;
import com.moriah.skillhub.sprint.entity.Task;
import com.moriah.skillhub.sprint.repository.AssignmentWindowRepository;
import com.moriah.skillhub.sprint.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** build-plan.md feature 11: "assignment_windows created here — the PM defines weekly windows
 * with a due date and an optional linked task." A separate, small service (not folded into
 * {@code SprintService}) for single-responsibility clarity — {@code SprintController} exposes it
 * (feature 09's "one controller, per-method paths" precedent), not a dedicated
 * {@code AssignmentWindowController}, since architecture.md's package diagram names only two
 * controllers for {@code sprint/} and this feature already deviates from that diagram once (see
 * {@code AssignmentWindow}'s Javadoc for the entity-placement decision). */
@Service
@RequiredArgsConstructor
public class AssignmentWindowService {

    private final AssignmentWindowRepository assignmentWindowRepository;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final TaskRepository taskRepository;

    @Transactional
    public AssignmentWindowResponse create(Long callerUserId, CreateAssignmentWindowRequest request) {
        Batch batch = requireBatch(request.batchId());
        batchService.requireOwnerOrAdmin(callerUserId, batch);

        if (assignmentWindowRepository.existsByBatchIdAndWeekStart(batch.getId(), request.weekStart())) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_WINDOW_WEEK_TAKEN);
        }

        AssignmentWindow window = new AssignmentWindow();
        window.setBatch(batch);
        window.setWeekStart(request.weekStart());
        window.setWeekEnd(request.weekEnd());
        window.setDueAt(request.dueAt());
        if (request.taskId() != null) {
            window.setTask(requireTaskInBatch(request.taskId(), batch.getId()));
        }
        assignmentWindowRepository.save(window);

        return toResponse(window);
    }

    /** build-plan.md feature 11 verify line: "Assignment windows can be created and listed per
     * batch." No dedicated endpoint is named for this in the endpoint list — {@code GET
     * /api/v1/assignment-windows?batchId=} is added to satisfy the verify line itself, mirroring
     * {@code GET /sprints?batchId=}'s shape exactly. */
    @Transactional(readOnly = true)
    public PageResponse<AssignmentWindowResponse> listByBatch(Long batchId, Pageable pageable) {
        return PageResponse.from(assignmentWindowRepository.findByBatchIdOrderByWeekStartAsc(batchId, pageable)
                .map(this::toResponse));
    }

    private Task requireTaskInBatch(Long taskId, Long batchId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TASK_NOT_FOUND, taskId));
        if (!task.getSprint().getBatch().getId().equals(batchId)) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_WINDOW_TASK_WRONG_BATCH);
        }
        return task;
    }

    private Batch requireBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, batchId));
    }

    private AssignmentWindowResponse toResponse(AssignmentWindow window) {
        Task task = window.getTask();
        return new AssignmentWindowResponse(
                window.getId(),
                window.getBatch().getId(),
                window.getWeekStart(),
                window.getWeekEnd(),
                window.getDueAt(),
                task != null ? task.getId() : null);
    }
}
