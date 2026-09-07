package com.moriah.skillhub.sprint;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.sprint.dto.CreateSprintRequest;
import com.moriah.skillhub.sprint.dto.SprintProgressProjection;
import com.moriah.skillhub.sprint.dto.SprintResponse;
import com.moriah.skillhub.sprint.dto.SprintTaskStatusCount;
import com.moriah.skillhub.sprint.dto.UpdateSprintRequest;
import com.moriah.skillhub.sprint.dto.VelocityProjection;
import com.moriah.skillhub.sprint.entity.Sprint;
import com.moriah.skillhub.sprint.entity.SprintStatus;
import com.moriah.skillhub.sprint.entity.TaskStatus;
import com.moriah.skillhub.sprint.repository.SprintRepository;
import com.moriah.skillhub.sprint.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * code-standards.md's own canonical {@code SprintService} example, verbatim in shape — this is
 * the feature that made the example true (see {@code Sprint}'s Javadoc for the {@code Batch}
 * shared-kernel reasoning this implies). {@code BatchRepository} is injected directly (loading
 * the {@code Batch} a sprint belongs to); {@code BatchService} is injected separately for the
 * cross-package ownership check ({@link BatchService#requireOwnerOrAdmin}) — two different,
 * both-legitimate reasons to depend on {@code batch/}, matching how {@code BatchService} itself
 * injects both {@code UserRepository} (shared-kernel entity) and {@code EntitlementService}
 * (sibling-module service).
 */
@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository sprintRepository;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final TaskRepository taskRepository;

    @Transactional
    public SprintResponse create(Long callerUserId, CreateSprintRequest request) {
        Batch batch = requireBatch(request.batchId());
        batchService.requireOwnerOrAdmin(callerUserId, batch);

        if (sprintRepository.existsByBatchIdAndSprintNumber(batch.getId(), request.sprintNumber())) {
            throw new BusinessException(ErrorCode.SPRINT_NUMBER_TAKEN);
        }
        if (sprintRepository.existsOverlapping(batch.getId(), null, request.startDate(), request.endDate())) {
            throw new BusinessException(ErrorCode.SPRINT_DATE_OVERLAP);
        }

        Sprint sprint = new Sprint();
        sprint.setBatch(batch);
        sprint.setSprintNumber(request.sprintNumber());
        sprint.setGoal(request.goal());
        sprint.setStartDate(request.startDate());
        sprint.setEndDate(request.endDate());
        sprint.setPlannedPoints(request.plannedPoints());
        sprint.setStatus(SprintStatus.PLANNED);
        sprintRepository.save(sprint);

        return toResponse(sprint);
    }

    @Transactional(readOnly = true)
    public PageResponse<SprintResponse> listByBatch(Long batchId, Pageable pageable) {
        return PageResponse.from(sprintRepository.findByBatchIdOrderBySprintNumberAsc(batchId, pageable)
                .map(this::toResponse));
    }

    @Transactional
    public SprintResponse update(Long callerUserId, Long sprintId, UpdateSprintRequest request) {
        Sprint sprint = requireSprint(sprintId);
        batchService.requireOwnerOrAdmin(callerUserId, sprint.getBatch());

        if (sprintRepository.existsOverlapping(sprint.getBatch().getId(), sprint.getId(),
                request.startDate(), request.endDate())) {
            throw new BusinessException(ErrorCode.SPRINT_DATE_OVERLAP);
        }

        sprint.setGoal(request.goal());
        sprint.setStartDate(request.startDate());
        sprint.setEndDate(request.endDate());
        sprint.setPlannedPoints(request.plannedPoints());

        transitionStatus(sprint, request.status());

        return toResponse(sprint);
    }

    /** build-plan.md feature 11: "One ACTIVE sprint per batch; activating a sprint requires the
     * previous one COMPLETED." Reuses the exact same {@link #transitionStatus} invariants {@link
     * #update} does for a {@code PLANNED -> ACTIVE} request — this endpoint exists as a clearer,
     * dedicated entry point, not a looser one. That "not looser" promise is why re-activating an
     * already-{@code ACTIVE} sprint is rejected explicitly here rather than falling through to
     * {@link #transitionStatus}'s same-status no-op — that no-op exists for {@link #update}'s
     * convenience (editing goal/dates without forcing a status re-decision), not to make a
     * dedicated activation endpoint silently succeed on a no-op re-activation. */
    @Transactional
    public SprintResponse activate(Long callerUserId, Long sprintId) {
        Sprint sprint = requireSprint(sprintId);
        batchService.requireOwnerOrAdmin(callerUserId, sprint.getBatch());

        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new BusinessException(ErrorCode.SPRINT_INVALID_TRANSITION,
                    "Cannot activate a sprint that is not PLANNED (current status: %s).".formatted(sprint.getStatus()));
        }
        transitionStatus(sprint, SprintStatus.ACTIVE);

        return toResponse(sprint);
    }

    /** {@code code-standards.md}'s own "Velocity = sum of completed_points across a batch's
     * COMPLETED sprints" formula (build-plan.md feature 11 "Sensible defaults"), computed from
     * the same derived-record projection {@link SprintRepository#findVelocity} returns — never a
     * loaded-entity sum. No dedicated endpoint exists for this in build-plan.md's feature 11
     * endpoint list; {@code SprintResponse.completedPoints} already exposes the per-sprint figure
     * this rolls up from GET /sprints, and this method exists so the formula itself is
     * unit-testable directly, per the feature's own verify line ("Velocity is accurate"). */
    @Transactional(readOnly = true)
    public int totalVelocity(Long batchId) {
        return sprintRepository.findVelocity(batchId).stream()
                .mapToInt(VelocityProjection::completedPoints)
                .sum();
    }

    /** {@code CertificateService#issue}'s eligibility gate (build-plan.md feature 20: "all
     * sprints closed") — a cross-package service call, never {@code SprintRepository} directly.
     * A batch with zero sprints passes vacuously — build-plan.md's own decision-log doesn't spec a
     * guard against that edge case, and inventing one here would reject a legitimate short-track
     * batch that never used the sprint feature at all. */
    @Transactional(readOnly = true)
    public boolean allSprintsClosed(Long batchId) {
        return !sprintRepository.existsByBatchIdAndStatusNot(batchId, SprintStatus.COMPLETED);
    }

    /** {@code ClientProjectService#progress}'s data source (build-plan.md feature 21: "GET
     * /clients/projects/{id}/progress returns burndown and milestone completion") — a
     * cross-module service-interface call, never {@code SprintRepository}/{@code TaskRepository}
     * directly from {@code client/} (the same boundary {@code CertificateService}'s own three
     * eligibility checks already establish). Empty for a batch with no sprints yet — {@code
     * ClientProjectService} treats that the same way it treats a {@code null} target batch (a
     * zeroed progress shape, not an error). */
    @Transactional(readOnly = true)
    public List<SprintProgressProjection> progressForBatch(Long batchId) {
        return sprintRepository.findProgressForBatch(batchId);
    }

    /** FRS MSH-FR-PM-02/MSH-FR-BA-03 task-level detail alongside {@link #progressForBatch}'s
     * story-point burndown — {@code ClientProjectService#progress}'s companion data source, same
     * cross-module boundary as {@link #progressForBatch} (never {@code TaskRepository} directly
     * from {@code client/}). Keyed by sprint id, then status; a status with zero tasks in a given
     * sprint is simply absent from that sprint's inner map (the backing {@code GROUP BY} never
     * produces a zero-count row) — callers read it with {@code getOrDefault(status, 0L)}. */
    @Transactional(readOnly = true)
    public Map<Long, Map<TaskStatus, Long>> taskStatusCountsForBatch(Long batchId) {
        Map<Long, Map<TaskStatus, Long>> bySprintId = new HashMap<>();
        for (SprintTaskStatusCount row : taskRepository.countTaskStatusesForBatch(batchId)) {
            bySprintId.computeIfAbsent(row.sprintId(), id -> new EnumMap<>(TaskStatus.class))
                    .put(row.status(), row.count());
        }
        return bySprintId;
    }

    /**
     * The single source of truth for every sprint status change, whether it arrives via {@link
     * #update} or {@link #activate}. A same-status request is a no-op (lets {@link #update} edit
     * goal/dates/points without forcing a status decision every time). Anything else must be one
     * of the two legal forward moves; everything else — backward, skipped, or into an unknown
     * value — is a 409 (build-plan.md feature 11 verify line: "Illegal transition returns 409").
     */
    private void transitionStatus(Sprint sprint, SprintStatus requested) {
        if (requested == sprint.getStatus()) {
            return;
        }
        if (sprint.getStatus() == SprintStatus.PLANNED && requested == SprintStatus.ACTIVE) {
            // requirePreviousSprintCompleted first, deliberately: in the normal sequential-
            // numbering flow it already subsumes requireNoOtherActiveSprint (the immediate
            // predecessor can only be ACTIVE, not COMPLETED, while it's the batch's active
            // sprint), so checking it first surfaces the specific, actionable error
            // (SPRINT_PREVIOUS_NOT_COMPLETED) instead of the generic one (SPRINT_ALREADY_ACTIVE)
            // whenever both would apply. requireNoOtherActiveSprint stays as a second,
            // defense-in-depth check for the data-anomaly case a later sprint was left ACTIVE
            // out of sequence.
            requirePreviousSprintCompleted(sprint);
            requireNoOtherActiveSprint(sprint);
            sprint.setStatus(SprintStatus.ACTIVE);
            return;
        }
        if (sprint.getStatus() == SprintStatus.ACTIVE && requested == SprintStatus.COMPLETED) {
            sprint.setStatus(SprintStatus.COMPLETED);
            return;
        }
        throw new BusinessException(ErrorCode.SPRINT_INVALID_TRANSITION,
                "Cannot move a sprint from %s to %s.".formatted(sprint.getStatus(), requested));
    }

    private void requireNoOtherActiveSprint(Sprint sprint) {
        sprintRepository.findByBatchIdAndStatus(sprint.getBatch().getId(), SprintStatus.ACTIVE)
                .filter(active -> !active.getId().equals(sprint.getId()))
                .ifPresent(active -> {
                    throw new BusinessException(ErrorCode.SPRINT_ALREADY_ACTIVE);
                });
    }

    /** Only the immediate predecessor (`sprintNumber - 1`) is checked, not every earlier sprint —
     * sufficient because activation is only ever legal in sequence: sprint N-1 can only have
     * reached COMPLETED by having itself passed this same check against N-2, and so on back to
     * sprint 1 (`/architect`-style reasoning, documented since this feature has no `/architect`
     * session). Sprint 1 has no predecessor to check. A missing predecessor (a gap in sprint
     * numbering) is treated the same as an incomplete one — there's nothing to prove "the
     * previous sprint" finished. */
    private void requirePreviousSprintCompleted(Sprint sprint) {
        if (sprint.getSprintNumber() <= 1) {
            return;
        }
        Sprint previous = sprintRepository
                .findByBatchIdAndSprintNumber(sprint.getBatch().getId(), sprint.getSprintNumber() - 1)
                .orElseThrow(() -> new BusinessException(ErrorCode.SPRINT_PREVIOUS_NOT_COMPLETED));
        if (previous.getStatus() != SprintStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.SPRINT_PREVIOUS_NOT_COMPLETED);
        }
    }

    private Batch requireBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, batchId));
    }

    Sprint requireSprint(Long sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SPRINT_NOT_FOUND, sprintId));
    }

    private SprintResponse toResponse(Sprint sprint) {
        return new SprintResponse(
                sprint.getId(),
                sprint.getBatch().getId(),
                sprint.getSprintNumber(),
                sprint.getGoal(),
                sprint.getStartDate(),
                sprint.getEndDate(),
                sprint.getStatus(),
                sprint.getPlannedPoints(),
                sprint.getCompletedPoints());
    }
}
