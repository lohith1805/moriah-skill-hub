package com.moriah.skillhub.crm.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A pure join row — a lead picked as part of one campaign's audience, at creation time or a
 * later edit. No {@code @ManyToOne} associations to {@link Lead}/{@link LeadCampaign}: same
 * "a join row is a fact, not a navigable entity graph" reasoning {@code UserRole} already
 * establishes for {@code user_roles}. */
@Entity
@Table(name = "lead_campaign_recipients")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LeadCampaignRecipient {

    @EmbeddedId
    private LeadCampaignRecipientId id;

    public LeadCampaignRecipient(Long campaignId, Long leadId) {
        this.id = new LeadCampaignRecipientId(campaignId, leadId);
    }
}
