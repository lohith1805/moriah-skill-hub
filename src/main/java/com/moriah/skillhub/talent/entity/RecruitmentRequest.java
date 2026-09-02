package com.moriah.skillhub.talent.entity;

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
 * A client's request to recruit a candidate from the talent pool (gap B1.9). {@code candidateId}
 * / {@code requestedBy} / {@code decidedBy} are bare user ids — this row only needs them to scope
 * a query or render a name, the same {@code Notification.userId} reasoning. A decision
 * ({@code decidedBy}, {@code decidedAt}, {@code decisionNote}) is only ever set once, moving the
 * request out of {@code PENDING}.
 */
@Entity
@Table(name = "recruitment_requests")
@Getter
@Setter
@NoArgsConstructor
public class RecruitmentRequest extends BaseEntity {

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "role_title", nullable = false, length = 150)
    private String roleTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "engagement_type", nullable = false, length = 20)
    private EngagementType engagementType;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecruitmentRequestStatus status = RecruitmentRequestStatus.PENDING;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
