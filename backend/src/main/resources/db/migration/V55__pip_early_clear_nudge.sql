-- Feature 17 follow-up. The nightly PIP job now does three extra things beyond trigger + the
-- window-elapsed auto-clear:
--   1. reconciles the ONE auto-seeded recovery milestone against the metric that raised the PIP —
--      auto-completing it when the student has genuinely recovered, and reverting a premature
--      manual "Verify" back to PENDING when the metric is still failing;
--   2. refuses the window-elapsed auto-clear unless that same trigger metric has actually recovered
--      (a careless Verify can no longer clear a student whose attendance is still 0%);
--   3. nudges the PM once, mid-window, when every clearance criterion is already met, so they can
--      clear early instead of waiting out the 15 days.
-- This column records that the one-time PIP_READY_TO_CLEAR nudge (3) has been sent, so it is not
-- re-sent every subsequent night the criteria still hold. NULL = not yet nudged; the dev
-- elapse-window helper resets it to NULL.
ALTER TABLE pip_records
    ADD COLUMN early_clear_nudged_at DATETIME(6) NULL AFTER outcome_at;
