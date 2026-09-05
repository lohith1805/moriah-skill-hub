package com.moriah.skillhub.crm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Composite key for {@link LeadCampaignRecipient} — same shape as {@code UserRoleId}. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class LeadCampaignRecipientId implements Serializable {

    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "lead_id")
    private Long leadId;
}
