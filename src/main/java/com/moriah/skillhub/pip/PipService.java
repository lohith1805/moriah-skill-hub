package com.moriah.skillhub.pip;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.metrics.StudentMetricsService;
import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.dto.PipMilestoneResponse;
import com.moriah.skillhub.pip.dto.PipRecordResponse;
import com.moriah.skillhub.pip.dto.PipRuleResponse;
import com.moriah.skillhub.pip.dto.ReviewPipRequest;
import com.moriah.skillhub.pip.dto.UpdatePipRuleRequest;
import com.moriah.skillhub.pip.entity.PipMilestone;
import com.moriah.skillhub.pip.entity.PipMilestoneStatus;
import com.moriah.skillhub.pip.entity.PipRecord;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import com.moriah.skillhub.pip.entity.PipStatus;
import com.moriah.skillhub.pip.repository.PipMilestoneRepository;
import com.moriah.skillhub.pip.repository.PipRecordRepository;
import com.moriah.skillhub.pip.repository.PipRuleRepository;
import com.moriah.skillhub.submission.WeeklyReviewService;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Every human-driven PIP endpoint: {@code GET /pip/me}, {@code GET /pip}, milestone completion,
 * the day-15 review, and rule config reads/updates. {@link PipEvaluationService} (the nightly
 * job's own bean) is deliberately separate — this class never triggers a record, only acts on one
 * that already exists. */
@Service
@RequiredArgsConstructor
public class PipService {

    private static final List<PipStatus> OPEN_STATUSES = List.of(PipStatus.TRIGGERED, PipStatus.IN_PROGRESS);

    private final PipRecordRepository pipRecordRepository;
    private final PipRuleRepository pipRuleRepository;
    private final PipMilestoneRepository pipMilestoneRepository;
    private final BatchService batchService;
    private final StudentMetricsService studentMetricsService;
    private final WeeklyReviewService weeklyReviewService;
    private final PipClearanceProperties pipClearanceProperties;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /** {@code sprint/TaskPullGuard}'s check (feature 11 stub, cleared at feature 17) — a proper
     * cross-package service call now that {@code pip/} has a full service layer, not the raw
     * {@code JdbcTemplate} workaround an earlier draft used (`/review` finding: that workaround's
     * cited precedent, {@code EntitlementGuard}, applies when the owning feature doesn't exist yet
     * — {@code pip/} already does). */
    @Transactional(readOnly = true)
    public boolean blocksPull(Long userId) {
        return pipRecordRepository.existsByUserIdAndStatusInAndBlocksTaskPullTrue(userId, OPEN_STATUSES);
    }

    @Transactional(readOnly = true)
    public PipRecordResponse me(Long callerUserId) {
        PipRecord record = pipRecordRepository.findByUserIdAndStatusIn(callerUserId, OPEN_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PIP_RECORD_NOT_FOUND));
        return toResponse(record);
    }

    /** Bulk-fetches milestones for the whole page in one flat query rather than one {@code
     * findByPipRecordId} call per record (code-standards.md "N+1 Prevention" — a `/review` finding
     * against the original per-record version of {@link #toResponse}). */
    @Transactional(readOnly = true)
    public PageResponse<PipRecordResponse> list(Long batchId, PipStatus status, Pageable pageable) {
        Page<PipRecord> page = pipRecordRepository.search(batchId, status, pageable);
        List<Long> recordIds = page.getContent().stream().map(PipRecord::getId).toList();
        Map<Long, List<PipMilestone>> milestonesByRecordId = pipMilestoneRepository.findByPipRecordIdIn(recordIds)
                .stream()
                .collect(Collectors.groupingBy(m -> m.getPipRecord().getId()));
        return PageResponse.from(page.map(record ->
                toResponse(record, milestonesByRecordId.getOrDefault(record.getId(), List.of()))));
    }

    @Transactional
    public PipMilestoneResponse completeMilestone(Long callerUserId, Long pipRecordId, Long milestoneId) {
        PipRecord record = requireRecord(pipRecordId);
        batchService.requireOwnerOrAdmin(callerUserId, record.getBatch());
        requireOpen(record);

        PipMilestone milestone = pipMilestoneRepository.findById(milestoneId)
                .filter(m -> m.getPipRecord().getId().equals(record.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PIP_MILESTONE_NOT_FOUND));
        if (milestone.getStatus() != PipMilestoneStatus.PENDING) {
            throw new BusinessException(ErrorCode.PIP_MILESTONE_ALREADY_COMPLETED);
        }

        milestone.setStatus(PipMilestoneStatus.COMPLETED);
        milestone.setCompletedAt(Instant.now());
        milestone.setVerifiedBy(userRepository.getReferenceById(callerUserId));

        auditLogService.record(callerUserId, "PIP_MILESTONE_COMPLETED", "PipMilestone", milestone.getId(),
                PipMilestoneStatus.PENDING, PipMilestoneStatus.COMPLETED);

        // The first milestone a PM completes is the "someone is actively working this" signal —
        // no dedicated endpoint exists to move TRIGGERED -> IN_PROGRESS any other way. Audited
        // separately from the milestone's own PENDING -> COMPLETED transition above (`/review`
        // finding: code-standards.md "Audit every mutation that affects ... PIP status" applies to
        // this PipRecord-level side effect too, not just the PipMilestone write that caused it).
        if (record.getStatus() == PipStatus.TRIGGERED) {
            record.setStatus(PipStatus.IN_PROGRESS);
            auditLogService.record(callerUserId, "PIP_STATUS_CHANGED", "PipRecord", record.getId(),
                    PipStatus.TRIGGERED, PipStatus.IN_PROGRESS);
        }

        return toMilestoneResponse(milestone);
    }

    /** build-plan.md feature 17: "clearance requires task completion ≥ 85% and a passed review,
     * both verified server-side against student_metrics — the PM cannot clear a student who does
     * not meet criteria." Only {@code CLEARED} is gated this way; {@code TERMINATED}/{@code
     * REASSIGNED} are a PM's judgment call the numbers don't second-guess. Any still-{@code
     * PENDING} milestone is resolved to {@code MISSED} the moment the record closes, regardless of
     * outcome — the window is over either way. */
    @Transactional
    public PipRecordResponse review(Long callerUserId, Long pipRecordId, ReviewPipRequest request) {
        PipRecord record = requireRecord(pipRecordId);
        batchService.requireOwnerOrAdmin(callerUserId, record.getBatch());
        requireOpen(record);
        if (request.outcome() == PipStatus.CLEARED) {
            requireClearanceCriteria(record);
        }

        // One switch both validates the outcome and maps it — a `/review` finding on an earlier
        // draft flagged validating outcome membership twice (a standalone if-chain, then this
        // switch's now-provably-unreachable default arm): two sources of truth that could drift
        // if PipStatus ever grows a value neither is updated for.
        BatchStudentStatus newBatchStatus = switch (request.outcome()) {
            case CLEARED -> BatchStudentStatus.ACTIVE;
            case TERMINATED -> BatchStudentStatus.TERMINATED;
            case REASSIGNED -> BatchStudentStatus.REASSIGNED;
            case TRIGGERED, IN_PROGRESS -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "outcome must be CLEARED, TERMINATED, or REASSIGNED.");
        };

        PipStatus previousStatus = record.getStatus();
        record.setStatus(request.outcome());
        record.setReviewedBy(userRepository.getReferenceById(callerUserId));
        record.setReviewNotes(request.reviewNotes());
        record.setOutcomeAt(Instant.now());

        List<PipMilestone> milestones = pipMilestoneRepository.findByPipRecordId(record.getId());
        for (PipMilestone milestone : milestones) {
            if (milestone.getStatus() == PipMilestoneStatus.PENDING) {
                milestone.setStatus(PipMilestoneStatus.MISSED);
            }
        }
        pipMilestoneRepository.saveAll(milestones);

        batchService.updatePipStatus(record.getBatch().getId(), record.getUser().getId(), newBatchStatus);

        auditLogService.record(callerUserId, "PIP_REVIEWED", "PipRecord", record.getId(),
                previousStatus, record.getStatus());

        return toResponse(record, milestones);
    }

    /** build-plan.md's "passed review" criterion — deliberately checked against {@link
     * WeeklyReviewService#hasUnsatisfactoryReviewSince}, windowed to this record's own {@code
     * startDate}, not {@code student_metrics.unsatisfactory_reviews} (lifetime-cumulative — see
     * that field's own Javadoc). Using the cumulative count here was a `/review` finding: it made
     * {@code CLEARED} permanently unreachable for any student who ever had one unsatisfactory
     * review, even long before this PIP started. */
    private void requireClearanceCriteria(PipRecord record) {
        StudentMetricProjection metrics = studentMetricsService
                .metricsFor(record.getUser().getId(), record.getBatch().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PIP_CLEARANCE_CRITERIA_NOT_MET));

        BigDecimal minCompletion = BigDecimal.valueOf(pipClearanceProperties.minTaskCompletionPercent());
        boolean taskCompletionMet = metrics.taskCompletionPercent() != null
                && metrics.taskCompletionPercent().compareTo(minCompletion) >= 0;
        boolean reviewPassed = !weeklyReviewService.hasUnsatisfactoryReviewSince(
                record.getUser().getId(), record.getStartDate());
        if (!taskCompletionMet || !reviewPassed) {
            throw new BusinessException(ErrorCode.PIP_CLEARANCE_CRITERIA_NOT_MET);
        }
    }

    @Transactional(readOnly = true)
    public List<PipRuleResponse> rules() {
        return pipRuleRepository.findAll().stream().map(this::toRuleResponse).toList();
    }

    @Transactional
    public PipRuleResponse updateRule(Long callerUserId, PipRuleCode ruleCode, UpdatePipRuleRequest request) {
        PipRule rule = pipRuleRepository.findByRuleCode(ruleCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PIP_RULE_NOT_FOUND));

        BigDecimal previousThreshold = rule.getThresholdValue();
        rule.setThresholdValue(request.thresholdValue());
        rule.setSeverity(request.severity());
        rule.setActive(request.active());

        auditLogService.record(callerUserId, "PIP_RULE_UPDATED", "PipRule", rule.getId(),
                previousThreshold, request.thresholdValue());

        return toRuleResponse(rule);
    }

    private PipRecord requireRecord(Long pipRecordId) {
        return pipRecordRepository.findById(pipRecordId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PIP_RECORD_NOT_FOUND));
    }

    private void requireOpen(PipRecord record) {
        if (!OPEN_STATUSES.contains(record.getStatus())) {
            throw new BusinessException(ErrorCode.PIP_RECORD_NOT_OPEN);
        }
    }

    private PipRecordResponse toResponse(PipRecord record) {
        return toResponse(record, pipMilestoneRepository.findByPipRecordId(record.getId()));
    }

    private PipRecordResponse toResponse(PipRecord record, List<PipMilestone> milestoneEntities) {
        List<PipMilestoneResponse> milestones = milestoneEntities.stream()
                .map(this::toMilestoneResponse)
                .toList();
        return new PipRecordResponse(
                record.getId(),
                record.getUser().getUuid(),
                record.getUser().getFullName(),
                record.getBatch().getId(),
                record.getBatch().getName(),
                record.getRuleCode(),
                record.getTriggerReason(),
                record.getSeverity(),
                record.getTriggeredAt(),
                record.getStartDate(),
                record.getEndDate(),
                record.getStatus(),
                record.isBlocksTaskPull(),
                record.getReviewedBy() == null ? null : record.getReviewedBy().getUuid(),
                record.getReviewNotes(),
                record.getOutcomeAt(),
                milestones);
    }

    private PipMilestoneResponse toMilestoneResponse(PipMilestone milestone) {
        return new PipMilestoneResponse(
                milestone.getId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getDueDate(),
                milestone.getStatus(),
                milestone.getCompletedAt(),
                milestone.getVerifiedBy() == null ? null : milestone.getVerifiedBy().getUuid());
    }

    private PipRuleResponse toRuleResponse(PipRule rule) {
        return new PipRuleResponse(rule.getRuleCode(), rule.getDescription(), rule.getThresholdValue(),
                rule.getWindowDays(), rule.getSeverity(), rule.isActive());
    }
}
