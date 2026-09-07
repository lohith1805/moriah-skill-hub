package com.moriah.skillhub.admin;

import com.moriah.skillhub.admin.dto.AdminMetricsOverviewResponse;
import com.moriah.skillhub.admin.dto.LeadFunnelStageSummary;
import com.moriah.skillhub.admin.dto.MonthlyRevenueSummary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@code GET /admin/metrics/overview} and {@code GET /admin/metrics/revenue}. build-plan.md
 * feature 22: "KPIs read from {@code student_metrics}, {@code v_revenue_monthly}, {@code
 * v_lead_funnel}, {@code v_batch_velocity} — never recomputed in Java" and "exports and heavy
 * metrics reads target the read replica" — every query here runs against {@link
 * #replicaJdbcTemplate}, never the primary.
 */
@Service
public class MetricsService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int REVENUE_HISTORY_MONTHS = 6;
    private static final int REVENUE_RANGE_DEFAULT_MONTHS = 12;

    private final JdbcTemplate replicaJdbcTemplate;

    public MetricsService(@Qualifier("replicaJdbcTemplate") JdbcTemplate replicaJdbcTemplate) {
        this.replicaJdbcTemplate = replicaJdbcTemplate;
    }

    /** build-plan.md feature 22: "Overview cached 5 minutes" — TTL configured on the
     * {@code adminMetricsOverview} cache in {@code RedisConfig}, following the exact {@code
     * @Cacheable}/per-cache-TTL precedent {@code EntitlementService.listActivePlans}/{@code
     * RedisConfig} already establish, rather than inventing a new caching mechanism. No {@code
     * key} needed — this method takes no arguments, so every caller shares the one cached entry
     * (Spring's default {@code SimpleKey.EMPTY}). */
    @Cacheable("adminMetricsOverview")
    public AdminMetricsOverviewResponse overview() {
        StudentMetricsAggregate aggregate = replicaJdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT user_id) AS active_student_count,
                       AVG(attendance_percent) AS avg_attendance_percent,
                       AVG(task_completion_percent) AS avg_task_completion_percent,
                       AVG(quiz_average_percent) AS avg_quiz_average_percent
                  FROM student_metrics
                """, (rs, rowNum) -> new StudentMetricsAggregate(
                rs.getLong("active_student_count"),
                rs.getBigDecimal("avg_attendance_percent"),
                rs.getBigDecimal("avg_task_completion_percent"),
                rs.getBigDecimal("avg_quiz_average_percent")));

        List<MonthlyRevenueSummary> recentRevenue = replicaJdbcTemplate.query("""
                SELECT revenue_month, currency, total_captured
                  FROM v_revenue_monthly
                 ORDER BY revenue_month DESC
                 LIMIT ?
                """, revenueRowMapper(), REVENUE_HISTORY_MONTHS);

        List<LeadFunnelStageSummary> leadFunnel = replicaJdbcTemplate.query("""
                SELECT status, SUM(lead_count) AS lead_count
                  FROM v_lead_funnel
                 GROUP BY status
                """, (rs, rowNum) -> new LeadFunnelStageSummary(rs.getString("status"), rs.getLong("lead_count")));

        VelocityAggregate velocity = replicaJdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(planned_points), 0) AS total_planned,
                       COALESCE(SUM(completed_points), 0) AS total_completed
                  FROM v_batch_velocity
                """, (rs, rowNum) -> new VelocityAggregate(rs.getLong("total_planned"), rs.getLong("total_completed")));

        BigDecimal velocityRatio = velocity.totalPlanned() == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(velocity.totalCompleted())
                        .divide(BigDecimal.valueOf(velocity.totalPlanned()), 4, RoundingMode.HALF_UP);

        return new AdminMetricsOverviewResponse(
                aggregate.activeStudentCount(),
                aggregate.avgAttendancePercent(),
                aggregate.avgTaskCompletionPercent(),
                aggregate.avgQuizAveragePercent(),
                recentRevenue,
                leadFunnel,
                velocity.totalPlanned(),
                velocity.totalCompleted(),
                velocityRatio);
    }

    /** {@code GET /admin/metrics/revenue?from=&to=} — both optional, defaulting to the trailing
     * 12 months so an omitted range still returns a small, bounded result rather than the whole
     * table's history (AGENTS.md: "no endpoint returns an unbounded collection"). Not paginated —
     * a month-granularity range is already bounded by the calendar, the same reasoning {@code
     * ClientProjectProgressResponse.burndown} already relies on for an unpaginated list nested in
     * a plain object response. */
    public List<MonthlyRevenueSummary> revenue(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusMonths(REVENUE_RANGE_DEFAULT_MONTHS - 1L);

        return replicaJdbcTemplate.query("""
                SELECT revenue_month, currency, total_captured
                  FROM v_revenue_monthly
                 WHERE revenue_month >= ? AND revenue_month <= ?
                 ORDER BY revenue_month
                """, revenueRowMapper(), MONTH_FORMAT.format(effectiveFrom), MONTH_FORMAT.format(effectiveTo));
    }

    private RowMapper<MonthlyRevenueSummary> revenueRowMapper() {
        return (rs, rowNum) -> new MonthlyRevenueSummary(
                rs.getString("revenue_month"), rs.getString("currency"), rs.getBigDecimal("total_captured"));
    }

    private record StudentMetricsAggregate(
            long activeStudentCount,
            BigDecimal avgAttendancePercent,
            BigDecimal avgTaskCompletionPercent,
            BigDecimal avgQuizAveragePercent) {
    }

    private record VelocityAggregate(long totalPlanned, long totalCompleted) {
    }
}
