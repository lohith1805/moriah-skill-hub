-- Feature 18 follow-up — fields the CRM pipeline UI needs that V12 didn't carry:
--   deal_value        : per-lead estimated / closed deal amount (drives the sales leaderboard's
--                        revenue column and per-agent commission). Nullable — not every lead has
--                        a number attached, and B2B tie-ups price later.
--   next_follow_up_at  : a denormalised cache of the furthest-out follow-up date set on any of
--                        this lead's activities, so the Kanban board can flag overdue cards
--                        without an N+1 over lead_activities. Written by LeadService.addActivity.
--   archived_at        : soft delete. DELETE /api/v1/leads/{id} sets it; every list/search query
--                        filters archived_at IS NULL. Mirrors the "campaigns deactivate, never
--                        row-delete" rule already established for lead_campaigns.

ALTER TABLE leads
    ADD COLUMN deal_value        DECIMAL(12, 2) NULL AFTER interested_plan_id,
    ADD COLUMN next_follow_up_at DATETIME(6)    NULL AFTER converted_user_id,
    ADD COLUMN archived_at       DATETIME(6)    NULL AFTER updated_at;

CREATE INDEX idx_leads_archived ON leads (archived_at);
