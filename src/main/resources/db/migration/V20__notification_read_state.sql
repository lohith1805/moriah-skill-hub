-- Feature: in-app notifications feed (frontend-integration gap B1.5, 2026-09-02).
-- The notifications table has always been dispatch-only (EMAIL/WHATSAPP via NotificationWorker).
-- The IN_APP channel row IS the notification (see InAppDispatcher's Javadoc) — the FE now reads
-- those rows back through GET /api/v1/notifications and marks them read. "Read" is a per-row
-- timestamp, deliberately not another `status` value: status (QUEUED/SENT/FAILED) is the
-- dispatch lifecycle and is owned by the worker; whether the recipient has seen the row is an
-- orthogonal concern. NULL read_at = unread.

ALTER TABLE notifications
    ADD COLUMN read_at DATETIME(6) NULL AFTER sent_at;

-- The feed query is (user_id, channel = 'IN_APP') ordered by created_at DESC, plus an
-- unread-only variant filtering read_at IS NULL. idx_notifications_user_status already covers
-- (user_id, status); this covers the feed's own access path.
CREATE INDEX idx_notifications_user_channel_created
    ON notifications (user_id, channel, read_at, created_at);
