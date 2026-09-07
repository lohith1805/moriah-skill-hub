package com.moriah.skillhub.common.job;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Every scheduled or async job writes one row per run (code-standards.md "Async and Scheduled
 * Work": "a job that silently did nothing must be diagnosable the next morning without a
 * debugger") — {@code common/}, not any one feature package, since {@code
 * AttendanceFinalisationJob}/{@code MetricsRefreshJob}/{@code PipEvaluationJob} (features
 * 13/16/17) will all need the exact same tracking {@code SubscriptionExpiryJob}/{@code
 * InvoiceGenerationJob} (feature 07) do.
 */
@Entity
@Table(name = "job_runs")
@Getter
@Setter
@NoArgsConstructor
public class JobRun extends BaseEntity {

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // columnDefinition matches V2's INT UNSIGNED exactly — see SubscriptionPlan (feature 07) for
    // why: Hibernate's default for an Integer column is plain INTEGER, which ddl-auto: validate
    // treats as a mismatch against any UNSIGNED narrower-than-BIGINT column.
    @Column(name = "items_processed", columnDefinition = "INT UNSIGNED")
    private Integer itemsProcessed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobRunStatus status = JobRunStatus.RUNNING;

    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
