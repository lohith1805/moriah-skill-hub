package com.moriah.skillhub.pip;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.metrics.StudentMetricsService;
import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.entity.PipMilestone;
import com.moriah.skillhub.pip.entity.PipRecord;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import com.moriah.skillhub.pip.entity.PipSeverity;
import com.moriah.skillhub.pip.entity.PipStatus;
import com.moriah.skillhub.pip.engine.PipRuleEvaluator;
import com.moriah.skillhub.pip.repository.PipMilestoneRepository;
import com.moriah.skillhub.pip.repository.PipRecordRepository;
import com.moriah.skillhub.pip.repository.PipRuleRepository;
import com.moriah.skillhub.user.UserService;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final List<PipRuleEvaluator> evaluators;

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
