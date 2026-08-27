package com.moriah.skillhub.metrics.repository;

import com.moriah.skillhub.metrics.dto.StudentMetricProjection;
import com.moriah.skillhub.metrics.entity.StudentMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Writes go through plain {@code JdbcTemplate} (code-standards.md "Native SQL only for...
 * metrics upserts") — {@code StudentMetricsService#refresh} never calls {@code save}/{@code
 * saveAll} here. The two reads below back {@code StudentMetricsService}'s bulk/single-user
 * projection methods that feature 17's {@code PipEvaluationService}/{@code PipService} call. */
public interface StudentMetricRepository extends JpaRepository<StudentMetric, Long> {

    /** {@code PipEvaluationService}'s whole cohort in one flat query (feature 17) — every row,
     * regardless of the underlying student's {@code batch_students} status, since {@code
     * StudentMetricsService#refresh} only ever writes/refreshes rows for {@code ACTIVE}/{@code
     * ON_PIP} students to begin with. */
    @Query("""
            SELECT new com.moriah.skillhub.metrics.dto.StudentMetricProjection(
                sm.user.id, sm.batch.id, sm.attendancePercent, sm.tasksOverdue48h, sm.taskCompletionPercent,
                sm.consecutiveAssignmentsMissed, sm.quizAveragePercent, sm.unsatisfactoryReviews,
                sm.daysSinceLastActivity)
              FROM StudentMetric sm
            """)
    List<StudentMetricProjection> findAllProjected();

    /** {@code PipService}'s clearance check (build-plan.md feature 17: "verified server-side
     * against student_metrics") — a single user's latest refreshed row. */
    @Query("""
            SELECT new com.moriah.skillhub.metrics.dto.StudentMetricProjection(
                sm.user.id, sm.batch.id, sm.attendancePercent, sm.tasksOverdue48h, sm.taskCompletionPercent,
                sm.consecutiveAssignmentsMissed, sm.quizAveragePercent, sm.unsatisfactoryReviews,
                sm.daysSinceLastActivity)
              FROM StudentMetric sm
             WHERE sm.user.id = :userId AND sm.batch.id = :batchId
            """)
    Optional<StudentMetricProjection> findProjectedByUserIdAndBatchId(
            @Param("userId") Long userId, @Param("batchId") Long batchId);
}
