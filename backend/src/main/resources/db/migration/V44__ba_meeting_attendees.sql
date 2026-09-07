-- Feature: named attendees on a BA meeting ("Client Pre-Project Discussions" in the frontend) —
-- the user's own ask: a role -> employee picker with a checkbox per person, so whoever is
-- checked gets emailed the invite and sees the meeting in their own dashboard. A pure join table,
-- no metadata worth carrying per row — same shape as user_roles / lead_campaign_recipients.
CREATE TABLE ba_meeting_attendees (
    meeting_id BIGINT UNSIGNED NOT NULL,
    user_id    BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (meeting_id, user_id),
    CONSTRAINT fk_ba_meeting_attendees_meeting FOREIGN KEY (meeting_id) REFERENCES ba_meetings (id),
    CONSTRAINT fk_ba_meeting_attendees_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_ba_meeting_attendees_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
