package com.moriah.skillhub.batch;

import com.moriah.skillhub.batch.dto.ActiveEnrollmentProjection;
import com.moriah.skillhub.batch.dto.ActiveMemberProjection;
import com.moriah.skillhub.batch.dto.AddStudentRequest;
import com.moriah.skillhub.batch.dto.AssignBatchProjectsRequest;
import com.moriah.skillhub.batch.dto.BatchProjectResponse;
import com.moriah.skillhub.batch.dto.BatchResponse;
import com.moriah.skillhub.batch.dto.BatchStudentResponse;
import com.moriah.skillhub.batch.dto.CreateBatchRequest;
import com.moriah.skillhub.batch.dto.GraduationResult;
import com.moriah.skillhub.batch.dto.PendingAllocationResponse;
import com.moriah.skillhub.batch.dto.UpdateBatchRequest;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.entity.BatchProject;
import com.moriah.skillhub.batch.entity.BatchStudent;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.batch.repository.BatchProjectRepository;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.batch.repository.BatchStudentRepository;
import com.moriah.skillhub.batch.repository.PendingBatchAllocationRepository;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.project.entity.Project;
import com.moriah.skillhub.project.entity.ProjectStatus;
import com.moriah.skillhub.project.repository.ProjectRepository;
import com.moriah.skillhub.sprint.entity.TaskStatus;
import com.moriah.skillhub.sprint.repository.TaskRepository;
import com.moriah.skillhub.subscription.EntitlementService;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manual, PM/Admin-driven batch CRUD and student management. The automatic path — a paying
 * student placed into a batch by the payment webhook — is {@link BatchAllocationService}, a
 * separate class deliberately (architecture.md package diagram lists both).
 */
@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchRepository batchRepository;
    private final BatchStudentRepository batchStudentRepository;
    private final PendingBatchAllocationRepository pendingBatchAllocationRepository;
    private final BatchProjectRepository batchProjectRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final EntitlementService entitlementService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    /** Read directly (not via {@code sprint/TaskService}) for the feature-20 graduation gate —
     * see {@code TaskRepository#countUnfinishedForStudentInBatch}'s Javadoc for the cycle it
     * avoids. */
    private final TaskRepository taskRepository;

    /** build-plan.md feature 10: "Creation restricted to TRAINER_PM and ADMIN; creator becomes
     * pm_id" — literally, with no exception for ADMIN (`/architect feature 10` reading). */
    @Transactional
    public BatchResponse create(Long callerUserId, CreateBatchRequest request) {
        User pm = userRepository.findById(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, callerUserId));

        Batch batch = new Batch();
        batch.setName(request.name());
        batch.setTrackCode(request.trackCode());
        batch.setPm(pm);
        batch.setPlanTierMinId(resolveTierMinId(request.planTierMinCode()));
        batch.setStartDate(request.startDate());
        batch.setEndDate(request.endDate());
        batch.setCapacity(request.capacity());
        batchRepository.save(batch);

        // Students who paid for a batch plan on this track before any batch existed are parked in
        // pending_batch_allocations. Draining that queue is an AFTER_COMMIT concern — done by
        // PendingAllocationDrainer, not inline, so BatchService keeps no dependency on
        // BatchAllocationService (they would otherwise form a cycle via UserService).
        eventPublisher.publishEvent(new BatchCreatedEvent(batch.getTrackCode()));

        return toResponse(batch, null);
    }

    /** {@code GET /api/v1/batches/pending-allocations} — students who paid for a batch plan but
     * have no matching batch yet ({@code pending_batch_allocations} rows still unresolved). A PM
     * screen shows this as "assignment pending"; the row clears itself the moment a matching batch
     * is created (see {@link BatchAllocationService#retryPendingForTrack}) or the PM adds them
     * manually. */
    @Transactional(readOnly = true)
    public List<PendingAllocationResponse> listPendingAllocations() {
        Map<Long, String> planCodes = entitlementService.planCodesById();
        return pendingBatchAllocationRepository.findByResolvedAtIsNullOrderByCreatedAtAsc().stream()
                .map(p -> new PendingAllocationResponse(
                        p.getUser().getUuid(),
                        p.getUser().getFullName(),
                        p.getUser().getEmail(),
                        p.getTrackCode(),
                        planCodes.get(p.getPlanId()),
                        p.getReason(),
                        p.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BatchResponse get(Long batchId) {
        return toResponse(requireBatch(batchId), null);
    }

    /** {@code GET /api/v1/batches/{id}} — a STUDENT may only read a batch they are enrolled in;
     * ADMIN / TRAINER_PM may read any. */
    @Transactional(readOnly = true)
    public BatchResponse get(Long batchId, Long callerUserId, boolean studentScoped) {
        if (studentScoped && batchStudentRepository.findByBatchIdAndUserId(batchId, callerUserId).isEmpty()) {
            throw new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, batchId);
        }
        return get(batchId);
    }

    @Transactional(readOnly = true)
    public PageResponse<BatchResponse> list(Pageable pageable) {
        Map<Long, String> planCodes = entitlementService.planCodesById();
        return PageResponse.from(batchRepository.findAll(pageable).map(batch -> toResponse(batch, planCodes)));
    }

    /** {@code GET /api/v1/batches} — {@code studentScoped} true (a STUDENT-only caller) narrows
     * the page to the batches they are enrolled in; ADMIN / TRAINER_PM get the full list. */
    @Transactional(readOnly = true)
    public PageResponse<BatchResponse> list(Long callerUserId, boolean studentScoped, Pageable pageable) {
        if (!studentScoped) {
            return list(pageable);
        }
        Map<Long, String> planCodes = entitlementService.planCodesById();
        return PageResponse.from(
                batchRepository.findEnrolledByUserId(callerUserId, pageable).map(batch -> toResponse(batch, planCodes)));
    }

    /** {@code GET /api/v1/batches/{id}/students} — the batch roster. PM/ADMIN only (route-gated),
     * and a TRAINER_PM must own the batch: {@link #requireOwnerOrAdmin} is the exact same check
     * every batch/sprint/task mutation already uses. Returns every enrolment row (ACTIVE, ON_PIP,
     * GRADUATED, …) so a PM screen can act on the {@code userUuid} — assign a task, graduate a
     * student, issue a letter — without a second lookup. */
    @Transactional(readOnly = true)
    public List<BatchStudentResponse> listStudents(Long callerUserId, Long batchId) {
        Batch batch = requireBatch(batchId);
        requireOwnerOrAdmin(callerUserId, batch);
        return batchStudentRepository.findByBatchIdOrderByJoinedAtAscIdAsc(batchId).stream()
                .map(BatchService::toStudentResponse)
                .toList();
    }

    /** {@code GET /api/v1/batches/{id}/projects} — the projects a Trainer/PM has already curated
     * onto this batch's own "Assign Projects" screen. Not the candidate pool to pick from (that's
     * just {@code GET /api/v1/projects?status=PUBLISHED&track=...}, no batch-specific endpoint
     * needed for it) — this is only what's already assigned. */
    @Transactional(readOnly = true)
    public List<BatchProjectResponse> listAssignedProjects(Long batchId) {
        requireBatch(batchId);
        List<Long> projectIds = batchProjectRepository.findProjectIdsByBatchId(batchId);
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return projectRepository.findAllById(projectIds).stream()
                .map(BatchService::toBatchProjectResponse)
                .toList();
    }

    /** {@code PUT /api/v1/batches/{id}/projects} — wholesale replace (empty list clears
     * everything), same idiom {@code LeadCampaignService#update} uses for its own recipients join
     * table. Every candidate must be {@code PUBLISHED} and share this batch's own {@code
     * trackCode} — "the PM can assign the projects to batches of their track only," the user's own
     * words — so a Data Analytics project can never land on a Full-Stack batch's screen by typo.
     * This is a curation record, not an access-control change: {@code ProjectService#list} keeps
     * showing every PUBLISHED project to every student either way. */
    @Transactional
    public List<BatchProjectResponse> assignProjects(Long callerUserId, Long batchId, AssignBatchProjectsRequest request) {
        Batch batch = requireBatch(batchId);
        requireOwnerOrAdmin(callerUserId, batch);

        List<Long> projectIds = request.projectIds();
        if (!projectIds.isEmpty()) {
            List<Project> projects = projectRepository.findAllById(projectIds);
            if (projects.size() != new java.util.HashSet<>(projectIds).size()) {
                throw new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND, projectIds);
            }
            for (Project project : projects) {
                if (project.getStatus() != ProjectStatus.PUBLISHED) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "\"" + project.getTitle() + "\" is not published yet.");
                }
                if (!Objects.equals(project.getTrack(), batch.getTrackCode())) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "\"" + project.getTitle() + "\" is tracked " + project.getTrack()
                                    + ", not this batch's " + batch.getTrackCode() + ".");
                }
            }
        }

        batchProjectRepository.deleteByIdBatchId(batchId);
        projectIds.forEach(projectId -> batchProjectRepository.save(new BatchProject(batchId, projectId)));

        return listAssignedProjects(batchId);
    }

    private static BatchProjectResponse toBatchProjectResponse(Project p) {
        return new BatchProjectResponse(p.getId(), p.getTitle(), p.getSlug(), p.getTrack(), p.getDifficulty(), p.getDomain());
    }

    private static BatchStudentResponse toStudentResponse(BatchStudent bs) {
        User u = bs.getUser();
        return new BatchStudentResponse(
                u.getUuid(),
                u.getFullName(),
                u.getEmail(),
                bs.getStatus(),
                bs.getJoinedAt(),
                bs.getGraduatedAt(),
                bs.getFinalScore());
    }

    @Transactional
    public BatchResponse update(Long callerUserId, Long batchId, UpdateBatchRequest request) {
        Batch batch = requireBatch(batchId);
        requireOwnerOrAdmin(callerUserId, batch);

        if (request.capacity() < batch.getEnrolledCount()) {
            throw new BusinessException(ErrorCode.BATCH_CAPACITY_BELOW_ENROLLED);
        }

        batch.setName(request.name());
        batch.setPlanTierMinId(resolveTierMinId(request.planTierMinCode()));
        batch.setStartDate(request.startDate());
        batch.setEndDate(request.endDate());
        batch.setCapacity(request.capacity());
        batch.setStatus(request.status());

        return toResponse(batch, null);
    }

    /** A manual PM/Admin add — deliberately does not re-check track/tier eligibility the way
     * {@link BatchAllocationService#allocate} does (`/architect feature 10`: a manual add is a
     * deliberate override). Capacity is still a hard DB constraint either way. Resolves (never
     * deletes) any open {@code pending_batch_allocations} row for the student. */
    @Transactional
    public BatchResponse addStudent(Long callerUserId, Long batchId, AddStudentRequest request) {
        Batch batch = requireBatch(batchId);
        requireOwnerOrAdmin(callerUserId, batch);
        User student = requireUserByUuid(request.userUuid());

        // ON_PIP counts as already-enrolled too (feature 17 addendum), same reasoning as
        // isActiveMember's — a flagged student still holds their one uq_batch_students_batch_user
        // row for this batch, so re-adding them would otherwise fall through to a raw unique-
        // constraint violation instead of this clean 409.
        boolean alreadyActive = batchStudentRepository.findByBatchIdAndUserId(batch.getId(), student.getId())
                .filter(bs -> bs.getStatus() == BatchStudentStatus.ACTIVE || bs.getStatus() == BatchStudentStatus.ON_PIP)
                .isPresent();
        if (alreadyActive) {
            throw new BusinessException(ErrorCode.BATCH_STUDENT_ALREADY_ENROLLED);
        }
        if (batchRepository.tryReserveSeat(batch.getId()) == 0) {
            throw new BusinessException(ErrorCode.BATCH_FULL);
        }

        BatchStudent batchStudent = new BatchStudent();
        batchStudent.setBatch(batch);
        batchStudent.setUser(student);
        batchStudentRepository.save(batchStudent);

        pendingBatchAllocationRepository.findByUserIdAndResolvedAtIsNull(student.getId())
                .ifPresent(pending -> {
                    pending.setResolvedAt(Instant.now());
                    pending.setResolvedBatch(batch);
                });

        // tryReserveSeat already committed the real increment via its own atomic UPDATE — this
        // is only so the response reflects it without a second SELECT.
        return toResponse(batch, null, batch.getEnrolledCount() + 1);
    }

    /** build-plan.md feature 10: "Removal sets batch_students.status to REASSIGNED — never
     * deletes, history is needed for PIP and certificates."
     * <p>
     * Deliberately still {@code ACTIVE}-only, unlike {@link #isActiveMember}/{@link #addStudent}
     * (feature 17 `/review` considered and rejected widening this one too): an {@code ON_PIP}
     * student has an open {@code pip_records} row, and that row's own lifecycle — {@code
     * PipService#review} — is the intended way to end their enrollment (it also closes the PIP
     * record and audits the outcome). This generic endpoint bypassing that would leave a {@code
     * pip_records} row dangling {@code TRIGGERED}/{@code IN_PROGRESS} against a student who's
     * already {@code REASSIGNED} out of the batch, with no review, no audit trail, and {@code
     * blocks_task_pull} still live. A PM who needs to remove an ON_PIP student goes through {@code
     * POST /pip/{id}/review} instead. */
    @Transactional
    public void removeStudent(Long callerUserId, Long batchId, String userUuid) {
        Batch batch = requireBatch(batchId);
        requireOwnerOrAdmin(callerUserId, batch);
        User student = requireUserByUuid(userUuid);

        BatchStudent batchStudent = batchStudentRepository.findByBatchIdAndUserId(batch.getId(), student.getId())
                .filter(bs -> bs.getStatus() == BatchStudentStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_STUDENT_NOT_FOUND));

        batchStudent.setStatus(BatchStudentStatus.REASSIGNED);
        batchRepository.releaseSeat(batch.getId());
    }

    private Long resolveTierMinId(String planTierMinCode) {
        return (planTierMinCode == null || planTierMinCode.isBlank())
                ? null
                : entitlementService.resolvePlanId(planTierMinCode);
    }

    /** ADMIN has full oversight and bypasses per-batch ownership (`/architect feature 10`
     * assumption); a TRAINER_PM may only manage batches where they are {@code pm}. Public —
     * feature 11's {@code SprintService}/{@code TaskService} reuse this exact check for
     * sprint/task mutation endpoints (build-plan.md feature 11: "same per-batch-PM-ownership-
     * with-ADMIN-bypass check BatchService already established, reuse that exact pattern") rather
     * than each duplicating their own copy of it. Calling a sibling module's service method, not
     * its repository/entity, per architecture.md's layer-boundary rule. */
    public void requireOwnerOrAdmin(Long callerUserId, Batch batch) {
        boolean isAdmin = SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name());
        if (!isAdmin && !batch.getPm().getId().equals(callerUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_BATCH_OWNER);
        }
    }

    /** feature 11: {@code TaskService.pull}/{@code .assign} need "is this user an ACTIVE member
     * of this batch" (build-plan.md: "must check the student is actually an ACTIVE member of the
     * task's sprint's batch") without touching {@code BatchStudentRepository} directly — {@code
     * BatchStudent} isn't covered by the {@code Batch}/{@code User} shared-kernel exception, so
     * this goes through the service, same as {@link #requireOwnerOrAdmin}.
     * <p>
     * {@code ON_PIP} counts as active too (feature 17 addendum) — a flagged student is still a
     * genuinely enrolled batch member, not a removed one, and this same check gates check-ins
     * ({@code AttendanceService}), quiz starts ({@code QuizService}), and task assign/pull ({@code
     * TaskService}). Excluding {@code ON_PIP} here would have made {@code TaskService.pull} throw
     * the generic {@code NOT_BATCH_MEMBER} for every PIP-flagged student before it ever reached
     * {@code TaskPullGuard}'s specific {@code TASK_PULL_BLOCKED_BY_PIP} check — silently blocking
     * pulls for every trigger rule, not just {@code PROJECT_DELAY} (build-plan.md: only that one
     * rule sets {@code blocks_task_pull = true}), and with the wrong error code besides. */
    @Transactional(readOnly = true)
    public boolean isActiveMember(Long batchId, Long userId) {
        return batchStudentRepository.findByBatchIdAndUserId(batchId, userId)
                .filter(bs -> bs.getStatus() == BatchStudentStatus.ACTIVE || bs.getStatus() == BatchStudentStatus.ON_PIP)
                .isPresent();
    }

    /** {@code TaskService#listMine} ({@code GET /api/v1/tasks/me}) — the batch ids a student is a
     * live member of ({@code ACTIVE}/{@code ON_PIP}), so tasks can be scoped without {@code
     * TaskService} reading {@code BatchStudent} itself (architecture.md layer rule: a feature
     * module calls another's service, not its repository/entity — {@link #isActiveMember} already
     * crosses exactly this boundary for the same reason). Empty when the student hasn't been
     * placed into a batch yet. */
    @Transactional(readOnly = true)
    public List<Long> activeBatchIdsForUser(Long userId) {
        return batchStudentRepository.findActiveBatchIdsByUserId(userId);
    }

    /** feature 13's {@code AttendanceFinalisationJob}: "every enrolled student with no attendance
     * row" — enrolled means {@code ACTIVE} or {@code ON_PIP} in {@code batch_students} at
     * finalisation time, the same definition {@link #isActiveMember} already uses (`/architect
     * feature 13` decision, widened for {@code ON_PIP} by a feature 17 `/review` finding — an
     * ON_PIP student who stops attending must still be auto-marked ABSENT, not silently excluded).
     * A student who graduated, was reassigned, or terminated before a standup was held is never
     * retroactively marked absent for it. One flat query for every batch the job needs this run,
     * not one call per batch — see {@code ActiveMemberProjection}'s Javadoc. */
    @Transactional(readOnly = true)
    public List<ActiveMemberProjection> activeMembersOf(Collection<Long> batchIds) {
        return batchStudentRepository.findActiveMembers(List.copyOf(batchIds));
    }

    /** {@code StudentMetricsService}'s whole-platform cohort in one flat query (feature 16) — the
     * cross-package-service, not-repository, boundary {@link #activeMembersOf} already established
     * for feature 13, extended here since the caller needs every batch's live cohort, not a
     * caller-supplied subset. See {@code ActiveEnrollmentProjection}'s Javadoc for why {@code
     * ON_PIP} is included alongside {@code ACTIVE}. */
    @Transactional(readOnly = true)
    public List<ActiveEnrollmentProjection> activeAndOnPipEnrollments() {
        return batchStudentRepository.findActiveAndOnPipEnrollments();
    }

    /** {@code PipService}'s status transitions (feature 17): {@code ON_PIP} the moment a rule
     * triggers, back to {@code ACTIVE} on a {@code CLEARED} outcome, or {@code TERMINATED}/{@code
     * REASSIGNED} on those outcomes — releasing the batch seat for the latter two, exactly like
     * {@link #removeStudent}'s existing {@code REASSIGNED} path, since the student is no longer
     * occupying an enrollment slot once either outcome lands. {@code CLEARED} needs no seat change:
     * an {@code ON_PIP} student was never released in the first place.
     * <p>
     * Guards the {@code ON_PIP} entry specifically: only a currently-{@code ACTIVE} student can be
     * placed on PIP. {@code /review} flagged this method as having no guard at all — defense in
     * depth against a caller (a bug, a retried request, a stale cohort row — see {@code
     * StudentMetricsService#currentCohortMetrics}'s own fix for the real scenario this closed)
     * accidentally flipping an already-{@code TERMINATED}/{@code REASSIGNED}/{@code GRADUATED}
     * student back onto PIP. The three review-outcome transitions have no equivalent guard because
     * {@code PipService#review} already enforces "only an open record can be reviewed" one layer
     * up, and reviewing always starts from {@code ON_PIP}. */
    @Transactional
    public void updatePipStatus(Long batchId, Long userId, BatchStudentStatus newStatus) {
        BatchStudent batchStudent = batchStudentRepository.findByBatchIdAndUserId(batchId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_STUDENT_NOT_FOUND));
        if (newStatus == BatchStudentStatus.ON_PIP && batchStudent.getStatus() != BatchStudentStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Student is not in a state that can be placed on PIP.");
        }
        batchStudent.setStatus(newStatus);
        if (newStatus == BatchStudentStatus.TERMINATED || newStatus == BatchStudentStatus.REASSIGNED) {
            batchRepository.releaseSeat(batchId);
        }
    }

    /** feature 19's letter-eligibility rule: "experience/relieving require GRADUATED or a clean
     * EXITED — never a terminated student." {@code HrLetterService} calls this before issuing
     * either letter type; a {@code GRADUATED} batch enrollment is the student-track path, an
     * {@code employees.status == EXITED} row (feature 19's own table) is the staff-track path —
     * two different signals for the same "left cleanly" fact, since {@code employees} and {@code
     * batch_students} are separate lifecycles for the two populations build-plan.md's feature 19
     * covers ("internal staff and internship-track students"). */
    @Transactional(readOnly = true)
    public boolean hasGraduated(Long userId) {
        return batchStudentRepository.existsByUserIdAndStatus(userId, BatchStudentStatus.GRADUATED);
    }

    /** {@code CertificateService#issue}'s eligibility gate (build-plan.md feature 20: "requires
     * GRADUATED") — batch-scoped, unlike {@link #hasGraduated}'s any-batch check for {@code
     * HrLetterService}'s exit-letter eligibility. A certificate is issued for a specific batch, so
     * "graduated from some batch, possibly a different one" would be the wrong question here. */
    @Transactional(readOnly = true)
    public boolean hasGraduatedFromBatch(Long batchId, Long userId) {
        return batchStudentRepository.findByBatchIdAndUserId(batchId, userId)
                .filter(bs -> bs.getStatus() == BatchStudentStatus.GRADUATED)
                .isPresent();
    }

    /** build-plan.md feature 20: "Graduation sign-off... The PM sets batch_students.status to
     * GRADUATED with graduated_at and graduated_by. Nothing else in the system sets this status,
     * and certificate issuance requires it." Deliberately {@code ACTIVE}-only, exactly like {@link
     * #removeStudent}'s own guard and for the identical reason: an {@code ON_PIP} student has an
     * open {@code pip_records} row whose lifecycle ({@code PipService#review}) is the only
     * legitimate way to change their enrollment status. A PM who needs to graduate an ON_PIP
     * student clears the PIP back to {@code ACTIVE} first via the normal review flow — this
     * endpoint never bypasses that. Releases the batch seat exactly like {@code updatePipStatus}'s
     * {@code TERMINATED}/{@code REASSIGNED} branches: a graduated student is no longer occupying
     * an enrollment slot either. Returns a {@link GraduationResult}, not the {@code BatchStudent}
     * entity itself — see that record's own Javadoc for why. */
    @Transactional
    public GraduationResult graduate(Long callerUserId, Long batchId, String userUuid) {
        Batch batch = requireBatch(batchId);
        requireOwnerOrAdmin(callerUserId, batch);
        User student = requireUserByUuid(userUuid);

        BatchStudent batchStudent = batchStudentRepository.findByBatchIdAndUserId(batch.getId(), student.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_STUDENT_NOT_FOUND));
        if (batchStudent.getStatus() != BatchStudentStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only an ACTIVE student can be graduated (current status: %s). Clear an open PIP back to ACTIVE first."
                            .formatted(batchStudent.getStatus()));
        }
        long unfinished = taskRepository.countUnfinishedForStudentInBatch(
                student.getId(), batch.getId(), TaskStatus.UNFINISHED);
        if (unfinished > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This student has %d unfinished sprint task(s). Every task assigned to them must be COMPLETED "
                            .formatted(unfinished) + "(or reassigned) before they can be graduated.");
        }

        Instant graduatedAt = Instant.now();
        batchStudent.setStatus(BatchStudentStatus.GRADUATED);
        batchStudent.setGraduatedAt(graduatedAt);
        batchStudent.setGraduatedBy(userRepository.getReferenceById(callerUserId));
        batchRepository.releaseSeat(batch.getId());

        return new GraduationResult(student.getId(), student.getFullName(), graduatedAt);
    }

    private Batch requireBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, batchId));
    }

    private User requireUserByUuid(String uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, uuid));
    }

    private BatchResponse toResponse(Batch batch, Map<Long, String> planCodes) {
        return toResponse(batch, planCodes, batch.getEnrolledCount());
    }

    private BatchResponse toResponse(Batch batch, Map<Long, String> planCodes, int enrolledCountOverride) {
        User pm = batch.getPm();
        String planTierMinCode = resolvePlanTierMinCode(batch.getPlanTierMinId(), planCodes);

        return new BatchResponse(
                batch.getId(),
                batch.getName(),
                batch.getTrackCode(),
                pm.getUuid(),
                pm.getFullName(),
                planTierMinCode,
                batch.getStartDate(),
                batch.getEndDate(),
                batch.getCapacity(),
                enrolledCountOverride,
                batch.getStatus());
    }

    private String resolvePlanTierMinCode(Long planTierMinId, Map<Long, String> planCodes) {
        if (planTierMinId == null) {
            return null;
        }
        return planCodes != null
                ? planCodes.get(planTierMinId)
                : entitlementService.findPlanCode(planTierMinId).orElse(null);
    }
}
