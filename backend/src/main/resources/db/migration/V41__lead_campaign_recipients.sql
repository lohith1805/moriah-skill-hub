-- Feature: pick a campaign's audience at creation time (frontend gap — the create-campaign form
-- only ever captured `target_leads` as a plain goal count; the actual recipient list was built
-- client-side, from scratch, only at Send time). A pure join row, no attributes of its own — same
-- shape as `user_roles` (V1): composite PK, no surrogate id, no timestamps.
CREATE TABLE lead_campaign_recipients (
    campaign_id BIGINT UNSIGNED NOT NULL,
    lead_id     BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (campaign_id, lead_id),
    CONSTRAINT fk_lead_campaign_recipients_campaign FOREIGN KEY (campaign_id) REFERENCES lead_campaigns (id),
    CONSTRAINT fk_lead_campaign_recipients_lead FOREIGN KEY (lead_id) REFERENCES leads (id),
    INDEX idx_lead_campaign_recipients_lead (lead_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
