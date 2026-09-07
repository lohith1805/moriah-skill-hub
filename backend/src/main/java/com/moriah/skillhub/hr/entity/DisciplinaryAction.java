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
 * A disciplinary action raised against an employee (gap B1.10). {@code employee} is a real
 * same-package {@code @ManyToOne}; {@code raisedBy} is a bare user id. {@code acknowledgedAt} /
 * {@code resolvedAt} are stamped once as the status advances.
 */
@Entity
@Table(name = "disciplinary_actions")
@Getter
@Setter
@NoArgsConstructor
public class DisciplinaryAction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "raised_by", nullable = false)
    private Long raisedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private DisciplinaryActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DisciplinarySeverity severity;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "action_taken", columnDefinition = "TEXT")
    private String actionTaken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisciplinaryStatus status = DisciplinaryStatus.OPEN;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
}
