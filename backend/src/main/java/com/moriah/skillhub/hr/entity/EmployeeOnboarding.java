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
 * A new employee's onboarding record (gap B1.10). {@code employee} is a real same-package
 * {@code @ManyToOne}; {@code initiatedBy}/{@code buddyId} are bare user ids. {@code checklist}
 * is pre-serialized JSON text handled only by the service.
 */
@Entity
@Table(name = "employee_onboardings")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeOnboarding extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    @Column(name = "buddy_id")
    private Long buddyId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnboardingStatus status = OnboardingStatus.NOT_STARTED;

    @Column(columnDefinition = "JSON")
    private String checklist;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "completed_at")
    private Instant completedAt;
}
