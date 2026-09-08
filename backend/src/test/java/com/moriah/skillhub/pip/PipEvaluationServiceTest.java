package com.moriah.skillhub.pip;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.notification.NotificationChannel;
import com.moriah.skillhub.common.notification.NotificationService;
import com.moriah.skillhub.metrics.StudentMetricsService;
import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.engine.AssignmentMissedRule;
import com.moriah.skillhub.pip.engine.AttendanceRule;
import com.moriah.skillhub.pip.engine.PipRuleEvaluator;
import com.moriah.skillhub.pip.engine.ProjectDelayRule;
import com.moriah.skillhub.pip.engine.QuizFailureRule;
import com.moriah.skillhub.pip.engine.ReviewFailedRule;
import com.moriah.skillhub.pip.engine.TaskAbandonedRule;
import com.moriah.skillhub.pip.entity.PipRecord;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import com.moriah.skillhub.pip.entity.PipSeverity;
import com.moriah.skillhub.pip.repository.PipMilestoneRepository;
import com.moriah.skillhub.pip.repository.PipRecordRepository;
import com.moriah.skillhub.pip.repository.PipRuleRepository;
import com.moriah.skillhub.user.UserService;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests the {@code @Transactional} core directly on {@link PipEvaluationService} — a separate
 * bean from {@link PipEvaluationJob} for the same self-invocation reasoning as {@code
 * StudentMetricsService}/{@code AttendanceFinalisationService}. The six real evaluator instances
 * are used (no mocking needed — they're pure functions, same reasoning {@code
 * QuizAttemptExpiryServiceTest} gives for using a real {@code GradingService}), so rule ordering
 * and threshold comparison are genuinely exercised, not just delegation verified. */
@ExtendWith(MockitoExtension.class)
class PipEvaluationServiceTest {

    @Mock
    private StudentMetricsService studentMetricsService;
    @Mock
    private PipRecordRepository pipRecordRepository;
    @Mock
    private PipRuleRepository pipRuleRepository;
    @Mock
    private PipMilestoneRepository pipMilestoneRepository;
    @Mock
    private BatchService batchService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private UserService userService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private com.moriah.skillhub.submission.WeeklyReviewService weeklyReviewService;

    private final PipClearanceProperties pipClearanceProperties = new PipClearanceProperties(85, 85);

    private static final List<PipRuleEvaluator> REAL_EVALUATORS = List.of(
            new AttendanceRule(), new ProjectDelayRule(), new AssignmentMissedRule(),
            new QuizFailureRule(), new ReviewFailedRule(), new TaskAbandonedRule());

    private PipEvaluationService service() {
        PipEvaluationService service = new PipEvaluationService(studentMetricsService, pipRecordRepository,
                pipRuleRepository, pipMilestoneRepository, batchService, userRepository, batchRepository,
                userService, auditLogService, notificationService, weeklyReviewService, pipClearanceProperties,
                REAL_EVALUATORS);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jobsZone", "Asia/Kolkata");
        return service;
    }

    private PipRule rule(PipRuleCode code, String threshold) {
        PipRule rule = new PipRule();
        rule.setRuleCode(code);
        rule.setThresholdValue(new BigDecimal(threshold));
        rule.setSeverity(PipSeverity.MEDIUM);
        rule.setActive(true);
        return rule;
    }

    private List<PipRule> allRulesAtDefaults() {
        return List.of(
                rule(PipRuleCode.ATTENDANCE_LOW, "75.00"),
                rule(PipRuleCode.PROJECT_DELAY, "1.00"),
                rule(PipRuleCode.ASSIGNMENT_MISSED, "2.00"),
                rule(PipRuleCode.QUIZ_FAILURE, "60.00"),
                rule(PipRuleCode.REVIEW_FAILED, "1.00"),
                rule(PipRuleCode.TASK_ABANDONED, "3.00"));
    }

    private User user(long id, String uuid) {
        User user = new User();
        user.setId(id);
        user.setUuid(uuid);
        user.setFullName("Test Student");
        return user;
    }

    private Batch batch(long id, long pmId) {
        Batch batch = new Batch();
        batch.setId(id);
        User pm = user(pmId, "pm-uuid");
        batch.setPm(pm);
        return batch;
    }

    private void stubReferences(long userId, long batchId, long pmId) {
        when(userRepository.getReferenceById(userId)).thenReturn(user(userId, "student-uuid"));
        when(batchRepository.getReferenceById(batchId)).thenReturn(batch(batchId, pmId));
    }

    private StudentMetricProjection cleanMetrics(long userId, long batchId) {
        return new StudentMetricProjection(userId, batchId, new BigDecimal("90.00"), 0,
                new BigDecimal("90.00"), 0, new BigDecimal("90.00"), 0, 0);
    }

    @Test
    void evaluate_emptyCohort_returnsZeroAndTouchesNothingElse() {
        when(studentMetricsService.currentCohortMetrics()).thenReturn(List.of());

        int count = service().evaluate();

        assertThat(count).isZero();
        verify(pipRecordRepository, never()).save(any());
        verify(pipRuleRepository, never()).findByActiveTrue();
    }

    @Test
    void evaluate_studentBelowAttendanceThreshold_triggersRecordAndMilestoneAndNotifies() {
        StudentMetricProjection metrics = new StudentMetricProjection(1L, 10L,
                new BigDecimal("62.00"), 0, new BigDecimal("90.00"), 0, new BigDecimal("90.00"), 0, 0);
        when(studentMetricsService.currentCohortMetrics()).thenReturn(List.of(metrics));
        when(pipRecordRepository.findOpenUserIds()).thenReturn(List.of());
        when(pipRuleRepository.findByActiveTrue()).thenReturn(allRulesAtDefaults());
        when(pipRecordRepository.save(any())).thenAnswer(inv -> {
            PipRecord r = inv.getArgument(0);
            r.setId(500L);
            return r;
        });
        stubReferences(1L, 10L, 99L);
        when(userService.findUserIdsByRole(RoleCode.HR_MANAGER)).thenReturn(List.of(77L));

        int count = service().evaluate();

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<PipRecord> captor = ArgumentCaptor.forClass(PipRecord.class);
        verify(pipRecordRepository).save(captor.capture());
        PipRecord saved = captor.getValue();
        assertThat(saved.getRuleCode()).isEqualTo(PipRuleCode.ATTENDANCE_LOW);
        assertThat(saved.getTriggerReason()).contains("62.00").contains("75.00");
        assertThat(saved.isBlocksTaskPull()).isFalse();

        verify(pipMilestoneRepository).save(any());
        verify(batchService).updatePipStatus(10L, 1L, BatchStudentStatus.ON_PIP);
        verify(auditLogService).record(isNull(), eq("PIP_TRIGGERED"), eq("PipRecord"), eq(500L), any(), any());

        verify(notificationService, times(3)).enqueueAfterCommit(anyLong(), eq(NotificationChannel.IN_APP),
                eq("PIP_TRIGGERED"), any());
    }

    @Test
    void evaluate_projectDelayTrigger_setsBlocksTaskPullTrue() {
        StudentMetricProjection metrics = new StudentMetricProjection(1L, 10L,
                new BigDecimal("90.00"), 2, new BigDecimal("90.00"), 0, new BigDecimal("90.00"), 0, 0);
        when(studentMetricsService.currentCohortMetrics()).thenReturn(List.of(metrics));
        when(pipRecordRepository.findOpenUserIds()).thenReturn(List.of());
        when(pipRuleRepository.findByActiveTrue()).thenReturn(allRulesAtDefaults());
        when(pipRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubReferences(1L, 10L, 99L);
        when(userService.findUserIdsByRole(RoleCode.HR_MANAGER)).thenReturn(List.of());

        service().evaluate();

        ArgumentCaptor<PipRecord> captor = ArgumentCaptor.forClass(PipRecord.class);
        verify(pipRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getRuleCode()).isEqualTo(PipRuleCode.PROJECT_DELAY);
        assertThat(captor.getValue().isBlocksTaskPull()).isTrue();
    }

    @Test
    void evaluate_studentAlreadyHasOpenRecord_isSkippedEntirely() {
        StudentMetricProjection metrics = new StudentMetricProjection(1L, 10L,
                new BigDecimal("10.00"), 5, new BigDecimal("10.00"), 5, new BigDecimal("10.00"), 5, 10);
        when(studentMetricsService.currentCohortMetrics()).thenReturn(List.of(metrics));
        when(pipRecordRepository.findOpenUserIds()).thenReturn(List.of(1L));
        when(pipRuleRepository.findByActiveTrue()).thenReturn(allRulesAtDefaults());

        int count = service().evaluate();

        assertThat(count).isZero();
        verify(pipRecordRepository, never()).save(any());
        verify(batchService, never()).updatePipStatus(any(), any(), any());
    }

    @Test
    void evaluate_studentTripsMultipleRules_onlyFirstInDeclarationOrderTriggers() {
        // Both ATTENDANCE_LOW and PROJECT_DELAY cross their thresholds — ATTENDANCE_LOW comes
        // first in PipRuleCode's declaration order, so it alone should fire.
        StudentMetricProjection metrics = new StudentMetricProjection(1L, 10L,
                new BigDecimal("10.00"), 5, new BigDecimal("90.00"), 0, new BigDecimal("90.00"), 0, 0);
        when(studentMetricsService.currentCohortMetrics()).thenReturn(List.of(metrics));
        when(pipRecordRepository.findOpenUserIds()).thenReturn(List.of());
        when(pipRuleRepository.findByActiveTrue()).thenReturn(allRulesAtDefaults());
        when(pipRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubReferences(1L, 10L, 99L);
        when(userService.findUserIdsByRole(RoleCode.HR_MANAGER)).thenReturn(List.of());

        int count = service().evaluate();

        assertThat(count).isEqualTo(1);
        verify(pipRecordRepository, times(1)).save(any());
        ArgumentCaptor<PipRecord> captor = ArgumentCaptor.forClass(PipRecord.class);
        verify(pipRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getRuleCode()).isEqualTo(PipRuleCode.ATTENDANCE_LOW);
    }

    @Test
    void evaluate_ruleInactive_neverTriggersEvenPastDefaultThreshold() {
        StudentMetricProjection metrics = new StudentMetricProjection(1L, 10L,
                new BigDecimal("10.00"), 0, new BigDecimal("90.00"), 0, new BigDecimal("90.00"), 0, 0);
        when(studentMetricsService.currentCohortMetrics()).thenReturn(List.of(metrics));
        when(pipRecordRepository.findOpenUserIds()).thenReturn(List.of());
        // ATTENDANCE_LOW omitted entirely — findByActiveTrue() only returns the other five.
        when(pipRuleRepository.findByActiveTrue()).thenReturn(allRulesAtDefaults().stream()
                .filter(r -> r.getRuleCode() != PipRuleCode.ATTENDANCE_LOW).toList());

        int count = service().evaluate();

        assertThat(count).isZero();
        verify(pipRecordRepository, never()).save(any());
    }

    @Test
    void evaluate_studentWithCleanMetrics_neverTriggers() {
        when(studentMetricsService.currentCohortMetrics()).thenReturn(List.of(cleanMetrics(1L, 10L)));
        when(pipRecordRepository.findOpenUserIds()).thenReturn(List.of());
        when(pipRuleRepository.findByActiveTrue()).thenReturn(allRulesAtDefaults());

        int count = service().evaluate();

        assertThat(count).isZero();
        verify(pipRecordRepository, never()).save(any());
    }
}
