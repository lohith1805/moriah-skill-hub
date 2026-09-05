-- FRS MSH-FR-DEV-03: two more optional reference links for a project's authoring form — a
-- reference-solution branch URL and a Loom/YouTube/Vimeo tutorial embed URL. Same plain-URL
-- convention as architecture_diagram_url/api_spec_url/er_diagram_url (V47): the Developer Projects
-- form already had "Reference Solution Branch URL" and "Loom / YouTube / Vimeo Embed URL" fields —
-- validated and submitted, but the API request never carried them and no column existed to hold
-- them, so both values were silently discarded on save. This adds the two columns actually
-- missing to make those existing form fields work.
ALTER TABLE projects
    ADD COLUMN reference_solution_url VARCHAR(500) NULL AFTER readme_content,
    ADD COLUMN video_tutorial_url     VARCHAR(500) NULL AFTER reference_solution_url;
