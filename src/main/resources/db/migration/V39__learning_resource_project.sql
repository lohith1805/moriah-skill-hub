-- Feature: let a curator additionally scope a Resource Library entry to one project (frontend
-- ask — the library already filters by cohort track; "add project as a filter also"). NULL =
-- not tied to any project (the common case). This is a filter dimension, not per-project
-- ownership — an entry can carry a track, a project, both, or neither.
ALTER TABLE learning_resources
    ADD COLUMN project_id BIGINT UNSIGNED NULL AFTER track,
    ADD CONSTRAINT fk_learning_resources_project
        FOREIGN KEY (project_id) REFERENCES projects (id),
    ADD INDEX idx_learning_resources_project (project_id);
