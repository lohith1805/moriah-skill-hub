-- Feature: a Developer now tags a project with a track (same free-string convention as
-- batches.track_code — see Batch.trackCode's own Javadoc) so a Trainer/PM can curate which
-- PUBLISHED, track-matching projects show up for their own batch. This is deliberately NOT a
-- visibility restriction — GET /api/v1/projects keeps showing every PUBLISHED project to every
-- student, unchanged (build-plan.md feature 15's own "no assigned batches on the project itself"
-- decision stands) — batch_projects is a lightweight curation record for the PM's own screen, not
-- an access-control table.
ALTER TABLE projects ADD COLUMN track VARCHAR(50) NULL AFTER domain;

CREATE TABLE batch_projects (
    batch_id   BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (batch_id, project_id),
    CONSTRAINT fk_batch_projects_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
    CONSTRAINT fk_batch_projects_project FOREIGN KEY (project_id) REFERENCES projects (id),
    INDEX idx_batch_projects_project (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
