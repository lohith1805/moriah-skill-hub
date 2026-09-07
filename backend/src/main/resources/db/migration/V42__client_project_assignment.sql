-- Feature: workload-aware round-robin assignment of a client project's submitted work to a BA
-- (at submission) and a developer (once a BA first signs off a BRD/FRS) — frontend gap: with
-- more than one BA/developer, every submitted project was visible to all of them with no owner
-- at all, so nothing routed and nobody's workload was tracked.
ALTER TABLE client_projects
    ADD COLUMN assigned_ba_id BIGINT UNSIGNED NULL AFTER client_id,
    ADD COLUMN assigned_developer_id BIGINT UNSIGNED NULL AFTER assigned_ba_id,
    -- Frontend gap: the submission form only ever captured title/scope/budget — nowhere for the
    -- client to add company/project context or discussion notes beyond the one-line scope.
    ADD COLUMN additional_notes TEXT NULL AFTER budget_range,
    ADD CONSTRAINT fk_client_projects_assigned_ba FOREIGN KEY (assigned_ba_id) REFERENCES users (id),
    ADD CONSTRAINT fk_client_projects_assigned_developer FOREIGN KEY (assigned_developer_id) REFERENCES users (id),
    ADD INDEX idx_client_projects_assigned_ba (assigned_ba_id),
    ADD INDEX idx_client_projects_assigned_developer (assigned_developer_id);
