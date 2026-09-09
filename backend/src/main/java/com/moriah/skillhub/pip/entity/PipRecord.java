package com.moriah.skillhub.pip.entity;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.common.entity.BaseEntity;
import com.moriah.skillhub.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** {@code user}/{@code batch}/{@code reviewedBy} are real {@code @ManyToOne} associations — {@code
 * User} and {@code Batch} are the shared-kernel exception, and a second {@code User} FK on the
 * same entity already has precedent ({@code Attendance.markedBy} alongside {@code Attendance.user}).
 * {@code ruleCode} is a bare enum column, not a {@code @ManyToOne PipRule} — {@code pip_rules} is a
 * fixed six-row config table, not a relationship this entity's reads ever need to traverse. */
@Entity
@Table(name = "pip_records")
@Getter
@Setter
@NoArgsConstructor
public class PipRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", nullable = false, length = 30)
    private PipRuleCode ruleCode;

    @Column(name = "trigger_reason", nullable = false, columnDefinition = "TEXT")
    private String triggerReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipSeverity severity;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipStatus status = PipStatus.TRIGGERED;

    @Column(name = "blocks_task_pull", nullable = false)
    private boolean blocksTaskPull;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    /** Read-only mirror of V11's {@code STORED} generated column — never set by application code
     * (Hibernate never writes an {@code insertable = false, updatable = false} column), only read,
     * so {@code PipRecordRepository#findOpenUserIds} can filter on {@code IS NOT NULL} and actually
     * use {@code uq_one_open_pip}'s index instead of an unindexed {@code status IN (...)} scan
     * (a `/review` finding — the query's own comment claimed this index usage without it). */
    @Column(name = "open_user_id", insertable = false, updatable = false)
    private Long openUserId;

    @Column(name = "outcome_at")
    private Instant outcomeAt;

    /** Set by {@code PipEvaluationService#nudgeEarlyRecoveries} the first night this still-open
     * record meets every clearance criterion before its window ends, so the "you can clear this
     * PIP early" PM notification is sent once, not re-sent every subsequent night. Null until then;
     * the dev {@code elapse-window} helper clears it back to null. */
    @Column(name = "early_clear_nudged_at")
    private Instant earlyClearNudgedAt;
}
