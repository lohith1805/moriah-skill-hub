-- Feature: a trainer schedules the daily standup with a video-call link (frontend gap — the
-- standup IS the daily meeting). Students see the link on their dashboard, join the call, and
-- check in. NULL = no link attached (an in-person standup, or a PM who scheduled without one).
ALTER TABLE standups
    ADD COLUMN meeting_link VARCHAR(500) NULL AFTER notes;
