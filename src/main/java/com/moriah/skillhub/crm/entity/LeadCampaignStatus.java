package com.moriah.skillhub.crm.entity;

/** Mirrors {@code chk_lead_campaigns_status} in {@code V24__lead_campaigns.sql}. {@code DELETE}
 * moves a campaign to {@code CANCELLED} rather than row-deleting it, so historical attribution
 * survives. */
public enum LeadCampaignStatus {
    PLANNED,
    ACTIVE,
    COMPLETED,
    CANCELLED
}
