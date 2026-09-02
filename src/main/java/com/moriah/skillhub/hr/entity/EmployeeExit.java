package com.moriah.skillhub.hr.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
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

/**
 * An employee offboarding record (gap B1.10). {@code employee} is a real same-package
 * {@code @ManyToOne}; {@code initiatedBy} is a bare user id (the row only needs it to render who
 * started the process). {@code clearanceChecklist} is pre-serialized JSON text — the service is
 * the only thing that reads or writes it, same treatment as {@code UserProfile.skills}.
 */
@Entity
@Table(name = "employee_exits")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeExit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_type", nullable = false, length = 20)
    private ExitType exitType;

    @Column(name = "last_working_day", nullable = false)
    private LocalDate lastWorkingDay;

    @Column(columnDefinition = "TEXT")
    private String reason;

    // columnDefinition matches V28's INT UNSIGNED exactly — same reasoning as SubscriptionPlan's
    // UNSIGNED columns.
    @Column(name = "notice_period_days", columnDefinition = "INT UNSIGNED")
    private Integer noticePeriodDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeExitStatus status = EmployeeExitStatus.INITIATED;

    @Column(name = "clearance_checklist", columnDefinition = "JSON")
    private String clearanceChecklist;

    @Column(name = "exit_interview_notes", columnDefinition = "TEXT")
    private String exitInterviewNotes;

    @Column(name = "completed_at")
    private Instant completedAt;
}
