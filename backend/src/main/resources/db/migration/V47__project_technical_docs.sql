-- FRS MSH-FR-DEV-03: optional reference links for a project's architecture diagram, Swagger/
-- OpenAPI spec, and database ER diagram — same plain-URL convention as the existing
-- starter_repo_url column, so a Developer can add any of these at authoring time or later, on an
-- already-published project, without a new upload pipeline. readme_content is LONGTEXT, not a
-- URL: the Developer Projects form already had a "Setup README instructions" textarea collecting
-- markdown — it was validated and submitted but the API request never carried it and no column
-- existed to hold it, so every README a Developer typed was silently discarded on save. This adds
-- the one column that was actually missing to make that existing form field work.
ALTER TABLE projects
    ADD COLUMN architecture_diagram_url VARCHAR(500) NULL AFTER starter_repo_url,
    ADD COLUMN api_spec_url             VARCHAR(500) NULL AFTER architecture_diagram_url,
    ADD COLUMN er_diagram_url           VARCHAR(500) NULL AFTER api_spec_url,
    ADD COLUMN readme_content           LONGTEXT     NULL AFTER er_diagram_url;
