package com.moriah.skillhub.crm.entity;

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

@Entity
@Table(name = "lead_activities")
@Getter
@Setter
@NoArgsConstructor
public class LeadActivity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    /** Null for {@link LeadActivityType#WHATSAPP_INBOUND} — the inbound webhook has no agent. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private User agent;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    private LeadActivityType activityType;

    @Column(length = 100)
    private String outcome;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "next_follow_up_at")
    private Instant nextFollowUpAt;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
