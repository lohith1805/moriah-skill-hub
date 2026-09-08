package com.moriah.skillhub.pip;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.entity.BatchStudentStatus;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.metrics.StudentMetricsService;
import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.pip.dto.ReviewPipRequest;
import com.moriah.skillhub.pip.dto.UpdatePipRuleRequest;
import com.moriah.skillhub.pip.entity.PipMilestone;
import com.moriah.skillhub.pip.entity.PipMilestoneStatus;
import com.moriah.skillhub.pip.entity.PipRecord;
import com.moriah.skillhub.pip.entity.PipRule;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import com.moriah.skillhub.pip.entity.PipSeverity;
import com.moriah.skillhub.pip.entity.PipStatus;
import com.moriah.skillhub.pip.repository.PipMilestoneRepository;
import com.moriah.skillhub.pip.repository.PipRecordRepository;
import com.moriah.skillhub.pip.repository.PipRuleRepository;
import com.moriah.skillhub.submission.WeeklyReviewService;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipServiceTest {

    @Mock
    private PipRecordRepository pipRecordRepository;
    @Mock
    private PipRuleRepository pipRuleRepository;
    @Mock
    private PipMilestoneRepository pipMilestoneRepository;
    @Mock
    private BatchService batchService;
    @Mock
    private StudentMetricsService studentMetricsService;
    @Mock
    private WeeklyReviewService weeklyReviewService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    @Mock
    private com.moriah.skillhub.sprint.repository.TaskRepository taskRepository;

    private final PipClearanceProperties pipClearanceProperties = new PipClearanceProperties(85, 85);

    private PipService service() {
        return new PipService(pipRecordRepository, pipRuleRepository, pipMilestoneRepository, batchService,
                studentMetricsService, weeklyReviewService, pipClearanceProperties, userRepository, auditLogService,
                taskRepository);
    }

    private User user(long id, String uuid) {
        User user = new User();
        user.setId(id);
        user.setUuid(uuid);
        user.setFullName("Test User");
        return user;
    }

    private Batch batch(long id) {
        Batch batch = new Batch();
        batch.setId(id);
        batch.setName("Batch-" + id);
        return batch;
    }

    private PipRecord openRecord(long id, PipStatus status) {
        PipRecord record = new PipRecord();
        record.setId(id);
        record.setUser(user(1L, "student-uuid"));
        record.setBatch(batch(10L));
        record.setRuleCode(PipRuleCode.ATTENDANCE_LOW);
        record.setTriggerReason("Attendance is 62.00%, below the 75.00% threshold.");
        record.setSeverity(PipSeverity.HIGH);
        record.setTriggeredAt(Instant.now());
        record.setStartDate(LocalDate.now().minusDays(1));
        record.setEndDate(LocalDate.now().plusDays(14));
        record.setStatus(status);
        return record;
    }

    private PipMilestone milestone(long id, PipRecord record, PipMilestoneStatus status) {
        PipMilestone milestone = new PipMilestone();
        milestone.setId(id);
        milestone.setPipRecord(record);
        milestone.setTitle("Attend every standup");
        milestone.setDueDate(LocalDate.now().plusDays(5));
        milestone.setStatus(status);
        return milestone;
    }

    // --- me / list ---

    @Test
    void me_noOpenRecord_throwsNotFound() {
        when(pipRecordRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().me(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PIP_RECORD_NOT_FOUND);
    }

    @Test
    void me_hasOpenRecord_returnsIt() {
        PipRecord record = openRecord(1L, PipStatus.TRIGGERED);
        when(pipRecordRepository.findByUserIdAndStatusIn(eq(1L), any())).thenReturn(Optional.of(record));
        when(pipMilestoneRepository.findByPipRecordId(1L)).thenReturn(List.of());

        var response = service().me(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(PipStatus.TRIGGERED);
    }

    @Test
    void list_delegatesToRepositorySearch() {
        Pageable pageable = PageRequest.of(0, 20);
        when(pipRecordRepository.search(10L, PipStatus.TRIGGERED, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty());

        PageResponse<?> response = service().list(10L, PipStatus.TRIGGERED, pageable);

        assertThat(response.content()).isEmpty();
    }

    // --- completeMilestone ---

    @Test
    void completeMilestone_pendingMilestoneOnTriggeredRecord_completesAndAdvancesToInProgress() {
        PipRecord record = openRecord(1L, PipStatus.TRIGGERED);
        PipMilestone milestone = milestone(5L, record, PipMilestoneStatus.PENDING);
        when(pipRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(pipMilestoneRepository.findById(5L)).thenReturn(Optional.of(milestone));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L, "pm-uuid"));

        var response = service().completeMilestone(9L, 1L, 5L);

        assertThat(response.status()).isEqualTo(PipMilestoneStatus.COMPLETED);
        assertThat(milestone.getCompletedAt()).isNotNull();
        assertThat(record.getStatus()).isEqualTo(PipStatus.IN_PROGRESS);
        verify(auditLogService).record(eq(9L), eq("PIP_MILESTONE_COMPLETED"), eq("PipMilestone"), eq(5L), any(), any());
    }

    @Test
    void completeMilestone_alreadyCompleted_throwsConflict() {
        PipRecord record = openRecord(1L, PipStatus.IN_PROGRESS);
        PipMilestone milestone = milestone(5L, record, PipMilestoneStatus.COMPLETED);
        when(pipRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(pipMilestoneRepository.findById(5L)).thenReturn(Optional.of(milestone));

        assertThatThrownBy(() -> service().completeMilestone(9L, 1L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PIP_MILESTONE_ALREADY_COMPLETED);
    }

    @Test
    void completeMilestone_recordAlreadyClosed_throwsConflict() {
        PipRecord record = openRecord(1L, PipStatus.CLEARED);
        when(pipRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service().completeMilestone(9L, 1L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PIP_RECORD_NOT_OPEN);
    }

    // --- review ---

    private StudentMetricProjection metricsMeetingClearance() {
        return new StudentMetricProjection(1L, 10L, new BigDecimal("90.00"), 0,
                new BigDecimal("90.00"), 0, new BigDecimal("90.00"), 0, 0);
    }

    @Test
    void review_clearedWithCriteriaMet_succeedsAndReactivatesBatchStatus() {
        PipRecord record = openRecord(1L, PipStatus.IN_PROGRESS);
        when(pipRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(studentMetricsService.metricsFor(1L, 10L)).thenReturn(Optional.of(metricsMeetingClearance()));
        when(pipMilestoneRepository.findByPipRecordId(1L)).thenReturn(List.of());
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L, "pm-uuid"));

        var response = service().review(9L, 1L, new ReviewPipRequest(PipStatus.CLEARED, "Improved across the board."));

        assertThat(response.status()).isEqualTo(PipStatus.CLEARED);
        assertThat(record.getOutcomeAt()).isNotNull();
        verify(batchService).updatePipStatus(10L, 1L, BatchStudentStatus.ACTIVE);
    }

    @Test
    void review_clearedWithTaskCompletionBelow85_throwsClearanceCriteriaNotMet() {
        PipRecord record = openRecord(1L, PipStatus.IN_PROGRESS);
        when(pipRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        StudentMetricProjection lowCompletion = new StudentMetricProjection(1L, 10L,
                new BigDecimal("90.00"), 0, new BigDecimal("70.00"), 0, new BigDecimal("90.00"), 0, 0);
        when(studentMetricsService.metricsFor(1L, 10L)).thenReturn(Optional.of(lowCompletion));

        assertThatThrownBy(() -> service().review(9L, 1L, new ReviewPipRequest(PipStatus.CLEARED, "notes")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PIP_CLEARANCE_CRITERIA_NOT_MET);
        verify(batchService, never()).updatePipStatus(any(), any(), any());
    }

    @Test
    void review_clearedWithUnsatisfactoryReviewSincePipStart_throwsClearanceCriteriaNotMet() {
        PipRecord record = openRecord(1L, PipStatus.IN_PROGRESS);
        when(pipRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(studentMetricsService.metricsFor(1L, 10L)).thenReturn(Optional.of(metricsMeetingClearance()));
        when(weeklyReviewService.hasUnsatisfactoryReviewSince(1L, record.getStartDate())).thenReturn(true);

        assertThatThrownBy(() -> service().review(9L, 1L, new ReviewPipRequest(PipStatus.CLEARED, "notes")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PIP_CLEARANCE_CRITERIA_NOT_MET);
    }

    @Test
    void review_terminated_bypassesClearanceGateAndReleasesSeat() {
        PipRecord record = openRecord(1L, PipStatus.IN_PROGRESS);
        when(pipRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(pipMilestoneRepository.findByPipRecordId(1L)).thenReturn(List.of());
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L, "pm-uuid"));

        var response = service().review(9L, 1L, new ReviewPipRequest(PipStatus.TERMINATED, "No improvement."));

        assertThat(response.status()).isEqualTo(PipStatus.TERMINATED);
        verify(studentMetricsService, never()).metricsFor(any(), any());
        verify(batchService).updatePipStatus(10L, 1L, BatchStudentStatus.TERMINATED);
    }

    @Test
    void review_alreadyClosedRecord_throwsConflict() {
        PipRecord record = openRecord(1L, PipStatus.CLEARED);
        when(pipRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service().review(9L, 1L, new ReviewPipRequest(PipStatus.TERMINATED, "x")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PIP_RECORD_NOT_OPEN);
    }

    @Test
    void review_stillOpenPendingMilestonesAreMarkedMissed() {
        PipRecord record = openRecord(1L, PipStatus.IN_PROGRESS);
        PipMilestone pending = milestone(5L, record, PipMilestoneStatus.PENDING);
        when(pipRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(pipMilestoneRepository.findByPipRecordId(1L)).thenReturn(List.of(pending));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L, "pm-uuid"));

        service().review(9L, 1L, new ReviewPipRequest(PipStatus.TERMINATED, "x"));

        assertThat(pending.getStatus()).isEqualTo(PipMilestoneStatus.MISSED);
    }

    // --- rules ---

    @Test
    void updateRule_unknownCode_throwsNotFound() {
        when(pipRuleRepository.findByRuleCode(PipRuleCode.ATTENDANCE_LOW)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateRule(1L, PipRuleCode.ATTENDANCE_LOW,
                new UpdatePipRuleRequest(new BigDecimal("70.00"), PipSeverity.HIGH, true)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PIP_RULE_NOT_FOUND);
    }

    @Test
    void updateRule_happyPath_updatesEveryField() {
        PipRule rule = new PipRule();
        rule.setId(1L);
        rule.setRuleCode(PipRuleCode.ATTENDANCE_LOW);
        rule.setThresholdValue(new BigDecimal("75.00"));
        rule.setSeverity(PipSeverity.HIGH);
        rule.setActive(true);
        when(pipRuleRepository.findByRuleCode(PipRuleCode.ATTENDANCE_LOW)).thenReturn(Optional.of(rule));

        var response = service().updateRule(1L, PipRuleCode.ATTENDANCE_LOW,
                new UpdatePipRuleRequest(new BigDecimal("70.00"), PipSeverity.MEDIUM, false));

        assertThat(response.thresholdValue()).isEqualByComparingTo("70.00");
        assertThat(response.windowDays()).isNull();
        assertThat(response.severity()).isEqualTo(PipSeverity.MEDIUM);
        assertThat(response.active()).isFalse();
        verify(auditLogService).record(eq(1L), eq("PIP_RULE_UPDATED"), eq("PipRule"), eq(1L), any(), any());
    }
}
