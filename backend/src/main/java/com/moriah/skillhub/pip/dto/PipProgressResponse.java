package com.moriah.skillhub.pip.dto;

import com.moriah.skillhub.pip.entity.PipRuleCode;
import com.moriah.skillhub.pip.entity.PipStatus;
import com.moriah.skillhub.sprint.entity.TaskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** {@code GET /api/v1/pip/{id}/progress} (PM) and {@code GET /api/v1/pip/me/progress} (student) —
 * everything the shared recovery-progress panel renders. Two layers: the PM-built milestone
 * checklist, and the numeric clearance gates ({@code clearanceCriteriaMet} is the exact pair
 * {@code POST /pip/{id}/review} enforces for CLEARED; attendance / overdue are advisory). */
public record PipProgressResponse(
        Long pipRecordId,
        String studentUuid,
        String studentFullName,
        PipRuleCode ruleCode,
        PipStatus status,
        LocalDate startDate,
        LocalDate endDate,
        int windowTotalDays,
        int daysElapsed,
        int daysRemaining,
        boolean windowElapsed,
        int milestonesTotal,
        int milestonesCompleted,
        List<PipMilestoneResponse> milestones,
        BigDecimal taskCompletionPercent,
        int taskCompletionTarget,
        boolean taskCompletionMet,
        BigDecimal attendancePercent,
        int attendanceTarget,
        boolean attendanceMet,
        Integer tasksOverdue,
        boolean overdueCleared,
        boolean weeklyReviewOk,
        boolean clearanceCriteriaMet,
        List<OutstandingTask> outstandingTasks
) {
    public record OutstandingTask(Long id, String title, TaskStatus status, Instant dueAt, boolean overdue) {
    }
}
