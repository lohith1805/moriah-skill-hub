package com.moriah.skillhub.crm.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A lead-generation campaign (gap B1.7). {@code createdBy} is a bare user id — a campaign row
 * only needs it for an ownership check, the same {@code Lead.interestedPlanId} / {@code
 * Notification.userId} reasoning. Money is {@code BigDecimal}; {@code targetLeads} is a plain
 * goal count.
 */
@Entity
@Table(name = "lead_campaigns")
@Getter
@Setter
@NoArgsConstructor
public class LeadCampaign extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadCampaignChannel channel;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal budget;

    // columnDefinition matches V24's INT UNSIGNED exactly — same reasoning as SubscriptionPlan's
    // UNSIGNED columns.
    @Column(name = "target_leads", columnDefinition = "INT UNSIGNED")
    private Integer targetLeads;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadCampaignStatus status = LeadCampaignStatus.PLANNED;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
