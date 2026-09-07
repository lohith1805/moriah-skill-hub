-- Feature: Lead-Gen Campaigns (frontend-integration gap B1.7). The CRM shipped leads +
-- activities + sales targets only; the FE's LeadCampaigns page needs a campaign object that
-- lead-gen agents plan and track. Standalone for now — attributing a lead to a campaign
-- (leads.campaign_id) is a follow-up that touches LeadService/CreateLeadRequest.

CREATE TABLE lead_campaigns (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150)    NOT NULL,
    channel     VARCHAR(20)     NOT NULL,
    description TEXT            NULL,
    start_date  DATE            NOT NULL,
    end_date    DATE            NULL,
    budget      DECIMAL(12, 2)  NULL,
    target_leads INT UNSIGNED   NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'PLANNED',
    created_by  BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_lead_campaigns_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_lead_campaigns_channel CHECK (channel IN (
        'EMAIL', 'SOCIAL', 'EVENT', 'REFERRAL', 'PAID_ADS', 'WEBINAR', 'OTHER')),
    CONSTRAINT chk_lead_campaigns_status CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    INDEX idx_lead_campaigns_status (status, start_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
