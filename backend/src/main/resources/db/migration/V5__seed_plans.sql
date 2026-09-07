-- Feature 02. The 5 subscription tiers, entitlement flags from project-overview.md's
-- Subscription Tiers table and architecture.md's entitlement model.
--
-- duration_days = 180 for every tier is a placeholder, confirmed with the user 2026-08-24 —
-- no per-tier duration is specified anywhere in the SRS/FRS/context files. Revisit with real
-- business values via a new migration (this one, once applied, is never edited).
-- max_projects is left NULL for the same reason: no per-tier value is specified anywhere, and
-- unlike duration_days nothing in build-plan.md's 24 features reads it yet.
--
-- Idempotent: safe to re-run, changes nothing after the first apply.

INSERT INTO subscription_plans
    (code, name, price_inr, tier_rank, duration_days, max_projects, mentor_support,
     allows_batch, allows_sprints, allows_pip, allows_internship_letter, allows_client_project, is_active)
VALUES
    ('STARTER',            'Starter',            3999.00,  1, 180, NULL, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE),
    ('PROFESSIONAL',       'Professional',       7999.00,  2, 180, NULL, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE),
    ('PROJECT_BASED',      'Project Based',      14999.00, 3, 180, NULL, FALSE, TRUE,  TRUE,  TRUE,  FALSE, FALSE, TRUE),
    ('INTERNSHIP',         'Internship',         19999.00, 4, 180, NULL, TRUE,  TRUE,  TRUE,  TRUE,  TRUE,  FALSE, TRUE),
    ('CORPORATE_PROGRAM',  'Corporate Program',  29999.00, 5, 180, NULL, TRUE,  TRUE,  TRUE,  TRUE,  TRUE,  TRUE,  TRUE)
ON DUPLICATE KEY UPDATE
    name                     = VALUES(name),
    price_inr                = VALUES(price_inr),
    tier_rank                = VALUES(tier_rank),
    duration_days            = VALUES(duration_days),
    max_projects              = VALUES(max_projects),
    mentor_support           = VALUES(mentor_support),
    allows_batch              = VALUES(allows_batch),
    allows_sprints             = VALUES(allows_sprints),
    allows_pip                = VALUES(allows_pip),
    allows_internship_letter  = VALUES(allows_internship_letter),
    allows_client_project     = VALUES(allows_client_project),
    is_active                 = VALUES(is_active);
