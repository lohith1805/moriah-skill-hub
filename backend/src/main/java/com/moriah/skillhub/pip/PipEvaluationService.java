package com.moriah.skillhub.pip;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.metrics.StudentMetricsService;
import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.dto.CreatePipRecordRequest;
import com.moriah.skillhub.pip.dto.PipMilestoneResponse;
import com.moriah.skillhub.pip.dto.PipRecordResponse;
import com.moriah.skillhub.pip.entity.PipMilestone;
import com.moriah.skillhub.pip.entity.PipMilestoneStatus;
import com.moriah.skillhub.pip.entity.PipRecord;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import com.moriah.skillhub.pip.entity.PipSeverity;
import com.moriah.skillhub.pip.entity.PipStatus;
import com.moriah.skillhub.pip.engine.PipRuleEvaluator;
import com.moriah.skillhub.pip.repository.PipMilestoneRepository;
import com.moriah.skillhub.pip.repository.PipRecordRepository;
import com.moriah.skillhub.pip.repository.PipRuleRepository;
import com.moriah.skillhub.submission.WeeklyReviewService;
import com.moriah.skillhub.user.UserService;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A separate bean from {@link PipEvaluationJob} — same self-invocation reasoning as {@code
 * StudentMetricsService}: a {@code @Scheduled} method on the job bean cannot also carry {@code
 * @Transactional} and call itself, so the atomic work lives here instead, reached through Spring's
 * proxy on every call.
 * <p>
 * {@code UserRepository}/{@code BatchRepository} are injected directly, not through {@code
 * UserService}/{@code BatchService}'s full APIs — the same shared-kernel exception {@code
 * SprintService} already established for reading a {@code Batch} it doesn't own (architecture.md:
 * only {@code User} and {@code Batch} may be real cross-package associations). Every other
 * cross-package read/write goes through the owning service: {@link StudentMetricsService} for the
 * cohort, {@link BatchService} for the {@code batch_students} status transition.
 * <p>
 * {@code evaluate()} is exactly the one-flat-query shape architecture.md requires: one query for
 * the whole cohort, one for every user who already has an open record (skipped entirely — "one
 * open record per user, a second run does not re-trigger" is enforced here by never attempting a
 * second insert for that user, not by catching {@code uq_one_open_pip}'s constraint violation),
 * one for the active rule set, one for the HR-manager recipient list every trigger notifies
 * (hoisted above the loop — a {@code /review} finding caught it re-querying per triggered student,
 * identical data every time). Nothing inside the per-student loop is a repository read; the six
 * evaluators are pure in-memory comparisons against data already in hand.
 * <p>
 * Evaluators are tried in descending <b>current</b> severity (read from {@code pip_rules} at the
 * start of each run, not the enum's declaration order) so that when a student trips more than one
 * rule on the same run, the one that actually matters most wins — an admin re-prioritising a rule
 * via {@code PUT /pip/rules/{code}} changes this ordering too, not just the trigger threshold.
 */
@Service
@RequiredArgsConstructor
public class PipEvaluationService {

    private final StudentMetricsService studentMetricsService;
    private final PipRecordRepository pipRecordRepository;
    private final PipRuleRepository pipRuleRepository;
    private final PipMilestoneRepository pipMilestoneRepository;
    private final BatchService batchService;
    private final UserRepository userRepository;
    private final BatchRepository batchRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final WeeklyReviewService weeklyReviewService;
    private final PipClearanceProperties pipClearanceProperties;
    private final List<PipRuleEvaluator> evaluators;

    private static final List<PipStatus> OPEN_STATUSES = List.of(PipStatus.TRIGGERED, PipStatus.IN_PROGRESS);

    /** Audit 2026-08-31 (M7): a PIP window's start/end dates must be computed in the jobs' zone —
     * on a UTC host the 02:00 IST run's {@code LocalDate.now()} is the previous calendar day, so
     * every 15-day remediation window would be a day short. */
    @Value("${moriah.jobs.zone}")
    private String jobsZone;

    @Transactional
    public int evaluate() {
        List<StudentMetricProjection> cohort = studentMetricsService.currentCohortMetrics();
        if (cohort.isEmpty()) {
            return 0;
        }

        Set<Long> alreadyOpenUserIds = new HashSet<>(pipRecordRepository.findOpenUserIds());
        Map<PipRuleCode, PipRule> rulesByCode = pipRuleRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(PipRule::getRuleCode, rule -> rule));
        List<PipRuleEvaluator> orderedEvaluators = evaluators.stream()
                .sorted(Comparator
                        .comparingInt((PipRuleEvaluator e) -> severityRank(e.ruleCode(), rulesByCode))
                        .reversed()
                        .thenComparingInt(e -> e.ruleCode().ordinal()))
                .toList();
        List<Long> hrUserIds = userService.findUserIdsByRole(RoleCode.HR_MANAGER);

        int triggeredCount = 0;
        for (StudentMetricProjection metric : cohort) {
            // `alreadyOpenUserIds` starts as the set of users with a pre-existing open record and
            // is extended below with every user triggered in *this* run. Audit 2026-08-31 (C2):
            // `currentCohortMetrics()` returns one row per (user_id, batch_id), and a student
            // enrolled in two batches at once is a legitimate data shape — without adding the
            // just-fired user here, the second batch row would call fire() again, hit
            // uq_one_open_pip on flush, and roll back the entire nightly run for every batch.
            if (alreadyOpenUserIds.contains(metric.userId())) {
                continue;
            }
            Optional<Trigger> trigger = firstTrigger(metric, orderedEvaluators, rulesByCode);
            if (trigger.isPresent()) {
                fire(metric, trigger.get(), hrUserIds);
                alreadyOpenUserIds.add(metric.userId());
                triggeredCount++;
            }
        }
        return triggeredCount;
    }

    private int severityRank(PipRuleCode ruleCode, Map<PipRuleCode, PipRule> rulesByCode) {
        PipRule rule = rulesByCode.get(ruleCode);
        return rule == null ? PipSeverity.LOW.ordinal() : rule.getSeverity().ordinal();
    }

    private Optional<Trigger> firstTrigger(StudentMetricProjection metric, List<PipRuleEvaluator> orderedEvaluators,
            Map<PipRuleCode, PipRule> rulesByCode) {
        for (PipRuleEvaluator evaluator : orderedEvaluators) {
            PipRule rule = rulesByCode.get(evaluator.ruleCode());
            if (rule == null) {
                continue;
            }
            Optional<String> reason = evaluator.evaluate(metric, rule);
            if (reason.isPresent()) {
                return Optional.of(new Trigger(rule, reason.get()));
            }
        }
        return Optional.empty();
    }

    private void fire(StudentMetricProjection metric, Trigger trigger, List<Long> hrUserIds) {
        LocalDate today = LocalDate.now(ZoneId.of(jobsZone));
        User student = userRepository.getReferenceById(metric.userId());
        Batch batch = batchRepository.getReferenceById(metric.batchId());

        PipRecord record = new PipRecord();
        record.setUser(student);
        record.setBatch(batch);
        record.setRuleCode(trigger.rule().getRuleCode());
        record.setTriggerReason(trigger.reason());
        record.setSeverity(trigger.rule().getSeverity());
        record.setTriggeredAt(Instant.now());
        record.setStartDate(today);
        record.setEndDate(today.plusDays(Constants.PIP_RECORD_DURATION_DAYS));
        record.setStatus(PipStatus.TRIGGERED);
        // build-plan.md feature 17: "Clears the feature 11 stub: PROJECT_DELAY sets
        // blocks_task_pull = true" — named for that one rule specifically, not every trigger.
        record.setBlocksTaskPull(trigger.rule().getRuleCode() == PipRuleCode.PROJECT_DELAY);
        record = pipRecordRepository.save(record);

        PipMilestone milestone = new PipMilestone();
        milestone.setPipRecord(record);
        milestone.setTitle(trigger.rule().getRuleCode().milestoneTitle());
        milestone.setDescription(trigger.reason());
        milestone.setDueDate(today.plusDays(Constants.PIP_MILESTONE_DUE_DAYS));
        pipMilestoneRepository.save(milestone);

        batchService.updatePipStatus(metric.batchId(), metric.userId(), BatchStudentStatus.ON_PIP);

        // No human caller for a nightly-job-driven trigger — audit_logs.user_id is nullable
        // (V2) specifically for this case, unlike every other audit call in this codebase, which
        // always has a real callerUserId from an authenticated request.
        auditLogService.record(null, "PIP_TRIGGERED", "PipRecord", record.getId(), null, record.getStatus());

        notifyTrigger(record, hrUserIds);
    }

    /** {@code POST /api/v1/pip} — a PM raises a PIP by hand for a qualitative concern the six
     * nightly rules don't detect. Same "record + one milestone + ON_PIP + audit + notify"
     * sequence as {@link #fire}, but with a real {@code callerUserId} on the audit row and the
     * PM's own reason/severity instead of an evaluator's. */
    @Transactional
    public PipRecordResponse createManual(Long callerUserId, CreatePipRecordRequest request) {
        User student = userRepository.findByUuid(request.studentUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, request.studentUuid()));
        Batch batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, request.batchId()));
        batchService.requireOwnerOrAdmin(callerUserId, batch);
        if (pipRecordRepository.existsByOpenUserId(student.getId())) {
            throw new BusinessException(ErrorCode.PIP_ALREADY_OPEN);
        }

        LocalDate today = LocalDate.now(ZoneId.of(jobsZone));
        PipRecord record = new PipRecord();
        record.setUser(student);
        record.setBatch(batch);
        record.setRuleCode(request.ruleCode());
        record.setTriggerReason(request.reason());
        record.setSeverity(request.severity());
        record.setTriggeredAt(Instant.now());
        record.setStartDate(today);
        record.setEndDate(today.plusDays(Constants.PIP_RECORD_DURATION_DAYS));
        record.setStatus(PipStatus.TRIGGERED);
        record.setBlocksTaskPull(request.ruleCode() == PipRuleCode.PROJECT_DELAY);
        record = pipRecordRepository.save(record);

        PipMilestone milestone = new PipMilestone();
        milestone.setPipRecord(record);
        milestone.setTitle(request.ruleCode().milestoneTitle());
        milestone.setDescription(request.reason());
        milestone.setDueDate(today.plusDays(Constants.PIP_MILESTONE_DUE_DAYS));
        pipMilestoneRepository.save(milestone);

        batchService.updatePipStatus(batch.getId(), student.getId(), BatchStudentStatus.ON_PIP);
        auditLogService.record(callerUserId, "PIP_TRIGGERED", "PipRecord", record.getId(), null, record.getStatus());
        notifyTrigger(record, userService.findUserIdsByRole(RoleCode.HR_MANAGER));

        return toRecordResponse(record, List.of(milestone));
    }

    /** The nightly job's close pass (feature 17 gap): every still-open PIP whose 15-day window
     * has fully elapsed is auto-CLEARED if the student now meets the exact criteria {@code POST
     * /pip/{id}/review} enforces (task completion ≥ target, no unsatisfactory review since start),
     * every recovery milestone is done, AND {@link #triggerRuleStillTrips the metric that raised
     * this PIP has genuinely recovered} — the last check is what stops a PM's premature "Verify"
     * at 0% attendance from auto-clearing a student who never recovered. If the window elapsed
     * and the student has NOT recovered, the record stays open (a PM still needs to TERMINATE /
     * REASSIGN) and the PM gets one escalation notice the day after the window closes. Never
     * auto-terminates. Runs after {@link #reconcileSeededMilestones}, which has already put the
     * seeded milestone's state in sync with its metric. */
    @Transactional
    public int autoResolveElapsed() {
        LocalDate today = LocalDate.now(ZoneId.of(jobsZone));
        List<PipRecord> elapsed = pipRecordRepository.findByStatusInAndEndDateBefore(OPEN_STATUSES, today);
        int resolved = 0;
        for (PipRecord record : elapsed) {
            List<PipMilestone> milestones = pipMilestoneRepository.findByPipRecordId(record.getId());
            boolean allMilestonesDone = !milestones.isEmpty()
                    && milestones.stream().allMatch(m -> m.getStatus() == PipMilestoneStatus.COMPLETED);
            StudentMetricProjection metrics = studentMetricsService
                    .metricsFor(record.getUser().getId(), record.getBatch().getId()).orElse(null);
            // Null task-completion (no metrics row, or no sprint tasks in the window) is
            // non-blocking — mirrors PipService.requireClearanceCriteria. A concrete percent
            // below target still blocks.
            boolean taskOk = taskCompletionOk(metrics);
            boolean reviewOk = !weeklyReviewService.hasUnsatisfactoryReviewSince(
                    record.getUser().getId(), record.getStartDate());
            boolean triggerResolved = !triggerRuleStillTrips(record, metrics);

            if (allMilestonesDone && taskOk && reviewOk && triggerResolved) {
                PipStatus previous = record.getStatus();
                record.setStatus(PipStatus.CLEARED);
                record.setReviewedBy(null); // system, not a PM
                record.setReviewNotes("Auto-cleared: 15-day window elapsed with every recovery milestone complete, "
                        + "all clearance criteria met, and the trigger metric recovered.");
                record.setOutcomeAt(Instant.now());
                batchService.updatePipStatus(record.getBatch().getId(), record.getUser().getId(),
                        BatchStudentStatus.ACTIVE);
                auditLogService.record(null, "PIP_AUTO_CLEARED", "PipRecord", record.getId(),
                        previous, PipStatus.CLEARED);
                notifyOutcome(record, "PIP_AUTO_CLEARED");
                resolved++;
            } else if (record.getEndDate().equals(today.minusDays(1))) {
                // Escalate exactly once — the first day after the window closes.
                notifyOutcome(record, "PIP_WINDOW_ELAPSED");
            }
        }
        return resolved;
    }

    /** Nightly pass, runs before {@link #autoResolveElapsed}: keep the one auto-seeded recovery
     * milestone honest against the metric that raised the PIP. The seeded milestone is the row
     * {@link #fire} / {@link #createManual} create from {@code ruleCode.milestoneTitle()} —
     * identified back by that exact title, since PM-added milestones carry PM-chosen titles and
     * must never be auto-touched. When the trigger metric has recovered the seeded milestone is
     * auto-completed ({@code verifiedBy = null}, i.e. by the system); when the metric is still
     * failing but the milestone has been marked COMPLETED anyway (a careless or optimistic PM
     * "Verify"), it is reverted to PENDING and the PM is notified. This is what makes the
     * window-elapsed auto-clear trustworthy without a human in the loop. */
    @Transactional
    public int reconcileSeededMilestones() {
        int changed = 0;
        for (PipRecord record : pipRecordRepository.findByStatusIn(OPEN_STATUSES)) {
            String seededTitle = record.getRuleCode().milestoneTitle();
            PipMilestone seeded = pipMilestoneRepository.findByPipRecordId(record.getId()).stream()
                    .filter(m -> seededTitle.equals(m.getTitle()))
                    .findFirst().orElse(null);
            if (seeded == null) {
                continue;
            }
            StudentMetricProjection metrics = studentMetricsService
                    .metricsFor(record.getUser().getId(), record.getBatch().getId()).orElse(null);
            boolean stillTrips = triggerRuleStillTrips(record, metrics);

            if (!stillTrips && seeded.getStatus() == PipMilestoneStatus.PENDING) {
                seeded.setStatus(PipMilestoneStatus.COMPLETED);
                seeded.setCompletedAt(Instant.now());
                seeded.setVerifiedBy(null);
                auditLogService.record(null, "PIP_MILESTONE_AUTO_COMPLETED", "PipMilestone", seeded.getId(),
                        PipMilestoneStatus.PENDING, PipMilestoneStatus.COMPLETED);
                changed++;
            } else if (stillTrips && seeded.getStatus() == PipMilestoneStatus.COMPLETED) {
                seeded.setStatus(PipMilestoneStatus.PENDING);
                seeded.setCompletedAt(null);
                seeded.setVerifiedBy(null);
                auditLogService.record(null, "PIP_MILESTONE_AUTO_REVERTED", "PipMilestone", seeded.getId(),
                        PipMilestoneStatus.COMPLETED, PipMilestoneStatus.PENDING);
                Map<String, Object> payload = Map.of(
                        "studentUuid", record.getUser().getUuid(),
                        "studentName", record.getUser().getFullName(),
                        "ruleCode", record.getRuleCode().name(),
                        "milestoneTitle", seeded.getTitle());
                notificationService.enqueueAfterCommit(record.getBatch().getPm().getId(),
                        NotificationChannel.IN_APP, "PIP_MILESTONE_AUTO_REVERTED", payload);
                changed++;
            }
        }
        return changed;
    }

    /** Nightly pass, runs after {@link #autoResolveElapsed}: a still-open PIP whose window has NOT
     * elapsed but which already meets every clearance criterion — nudge the PM once so they can
     * clear it early instead of waiting out the full 15 days. The PM still makes the call ({@code
     * POST /pip/{id}/review}); this only surfaces the opportunity. {@code early_clear_nudged_at}
     * makes it a one-time notice, not a nightly repeat. */
    @Transactional
    public int nudgeEarlyRecoveries() {
        LocalDate today = LocalDate.now(ZoneId.of(jobsZone));
        int nudged = 0;
        for (PipRecord record : pipRecordRepository
                .findByStatusInAndEndDateGreaterThanEqual(OPEN_STATUSES, today)) {
            if (record.getEarlyClearNudgedAt() != null) {
                continue;
            }
            List<PipMilestone> milestones = pipMilestoneRepository.findByPipRecordId(record.getId());
            boolean allMilestonesDone = !milestones.isEmpty()
                    && milestones.stream().allMatch(m -> m.getStatus() == PipMilestoneStatus.COMPLETED);
            if (!allMilestonesDone) {
                continue;
            }
            StudentMetricProjection metrics = studentMetricsService
                    .metricsFor(record.getUser().getId(), record.getBatch().getId()).orElse(null);
            boolean ready = taskCompletionOk(metrics)
                    && !weeklyReviewService.hasUnsatisfactoryReviewSince(
                            record.getUser().getId(), record.getStartDate())
                    && !triggerRuleStillTrips(record, metrics);
            if (!ready) {
                continue;
            }
            record.setEarlyClearNudgedAt(Instant.now());
            auditLogService.record(null, "PIP_READY_TO_CLEAR", "PipRecord", record.getId(),
                    null, record.getStatus());
            Map<String, Object> payload = Map.of(
                    "studentUuid", record.getUser().getUuid(),
                    "studentName", record.getUser().getFullName(),
                    "ruleCode", record.getRuleCode().name());
            notificationService.enqueueAfterCommit(record.getBatch().getPm().getId(),
                    NotificationChannel.IN_APP, "PIP_READY_TO_CLEAR", payload);
            nudged++;
        }
        return nudged;
    }

    /** Null task-completion — no metrics row, or a window with no sprint tasks — is non-blocking:
     * an attendance- or review-triggered PIP is not held open by a figure that can never be
     * computed. A concrete percent below target still blocks. Shared by every pass and {@code
     * PipService.requireClearanceCriteria} so they agree. */
    private boolean taskCompletionOk(StudentMetricProjection metrics) {
        return metrics == null || metrics.taskCompletionPercent() == null
                || metrics.taskCompletionPercent()
                        .compareTo(BigDecimal.valueOf(pipClearanceProperties.minTaskCompletionPercent())) >= 0;
    }

    /** Has the specific rule that raised this PIP recovered for the student? {@code
     * REVIEW_FAILED}'s evaluator reads a lifetime-cumulative count that never falls once a bad
     * review is on record, so for "is this PIP's own concern resolved?" it is asked directly
     * whether any unsatisfactory review has landed since the PIP started. Every other rule is
     * re-run through its own evaluator against the current metrics and threshold. Conservative on
     * missing data: no metrics row -> still tripping; rule retired / no evaluator -> nothing left
     * to hold the record open on. */
    private boolean triggerRuleStillTrips(PipRecord record, StudentMetricProjection metrics) {
        if (record.getRuleCode() == PipRuleCode.REVIEW_FAILED) {
            return weeklyReviewService.hasUnsatisfactoryReviewSince(
                    record.getUser().getId(), record.getStartDate());
        }
        if (metrics == null) {
            return true;
        }
        PipRule rule = pipRuleRepository.findByRuleCode(record.getRuleCode()).orElse(null);
        PipRuleEvaluator evaluator = evaluators.stream()
                .filter(e -> e.ruleCode() == record.getRuleCode())
                .findFirst().orElse(null);
        if (rule == null || evaluator == null) {
            return false;
        }
        return evaluator.evaluate(metrics, rule).isPresent();
    }

    /** {@code PIP_AUTO_CLEARED} -> student + PM; {@code PIP_WINDOW_ELAPSED} -> PM only. */
    private void notifyOutcome(PipRecord record, String templateCode) {
        Map<String, Object> payload = Map.of(
                "studentUuid", record.getUser().getUuid(),
                "studentName", record.getUser().getFullName(),
                "ruleCode", record.getRuleCode().name());
        Long pmUserId = record.getBatch().getPm().getId();
        notificationService.enqueueAfterCommit(pmUserId, NotificationChannel.IN_APP, templateCode, payload);
        if ("PIP_AUTO_CLEARED".equals(templateCode)) {
            notificationService.enqueueAfterCommit(record.getUser().getId(), NotificationChannel.IN_APP, templateCode, payload);
        }
    }

    private PipRecordResponse toRecordResponse(PipRecord record, List<PipMilestone> milestones) {
        List<PipMilestoneResponse> milestoneResponses = milestones.stream()
                .map(m -> new PipMilestoneResponse(m.getId(), m.getTitle(), m.getDescription(), m.getDueDate(),
                        m.getStatus(), m.getCompletedAt(),
                        m.getVerifiedBy() == null ? null : m.getVerifiedBy().getUuid()))
                .toList();
        return new PipRecordResponse(
                record.getId(), record.getUser().getUuid(), record.getUser().getFullName(),
                record.getBatch().getId(), record.getBatch().getName(), record.getRuleCode(),
                record.getTriggerReason(), record.getSeverity(), record.getTriggeredAt(),
                record.getStartDate(), record.getEndDate(), record.getStatus(), record.isBlocksTaskPull(),
                record.getReviewedBy() == null ? null : record.getReviewedBy().getUuid(),
                record.getReviewNotes(), record.getOutcomeAt(), milestoneResponses);
    }

    /** "notifications to student, PM and HR in afterCommit" (build-plan.md) — same
     * enqueueAfterCommit-per-recipient shape {@code BatchAllocationService#notifyPMs} already
     * established, extended to three recipient groups instead of one. {@code hrUserIds} is loaded
     * once per {@link #evaluate} run by the caller, not re-queried per triggered student — the HR
     * roster can't change mid-run. */
    private void notifyTrigger(PipRecord record, List<Long> hrUserIds) {
        Long studentUserId = record.getUser().getId();
        Long pmUserId = record.getBatch().getPm().getId();
        Map<String, Object> payload = Map.of(
                "studentUuid", record.getUser().getUuid(),
                "ruleCode", record.getRuleCode().name(),
                "severity", record.getSeverity().name());

        notificationService.enqueueAfterCommit(studentUserId, NotificationChannel.IN_APP, "PIP_TRIGGERED", payload);
        notificationService.enqueueAfterCommit(pmUserId, NotificationChannel.IN_APP, "PIP_TRIGGERED", payload);
        for (Long hrUserId : hrUserIds) {
            notificationService.enqueueAfterCommit(hrUserId, NotificationChannel.IN_APP, "PIP_TRIGGERED", payload);
        }
    }

    private record Trigger(PipRule rule, String reason) {
    }
}
