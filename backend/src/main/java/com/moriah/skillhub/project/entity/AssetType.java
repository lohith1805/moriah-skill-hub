package com.moriah.skillhub.project.entity;

/** V8's {@code chk_project_assets_type} CHECK constraint lists these four values — inferred by
 * that migration's own comment as the asset shapes a project brief plausibly needs. */
public enum AssetType {
    IMAGE,
    VIDEO,
    DOCUMENT,
    DESIGN_FILE
}
