-- Feature: scope curriculum content to a cohort track (frontend gap — a DEVELOPER authoring a
-- video lesson or a resource had no way to say which track it's for, so every student saw
-- everything). `track` is a free string matching `batches.track_code` (FULL_STACK,
-- DATA_ANALYTICS, PRODUCT_DESIGN, BACKEND_ENGINEERING, ...). NULL = shown to every track.
ALTER TABLE video_lessons
    ADD COLUMN track VARCHAR(30) NULL AFTER module_name;

ALTER TABLE learning_resources
    ADD COLUMN track VARCHAR(30) NULL AFTER category;
