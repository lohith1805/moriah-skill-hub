package com.moriah.skillhub.batch.dto;

import com.moriah.skillhub.project.entity.ProjectDifficulty;

/** One project curated onto a batch's "Assign Projects" screen — a summary shape, not the full
 * {@code ProjectResponse} (no assets/challenges; this list is for picking/reviewing, not
 * authoring). */
public record BatchProjectResponse(
        Long id,
        String title,
        String slug,
        String track,
        ProjectDifficulty difficulty,
        String domain
) {
}
