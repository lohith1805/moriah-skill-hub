package com.moriah.skillhub.project.dto;

import com.moriah.skillhub.project.entity.ProjectDifficulty;
import com.moriah.skillhub.project.entity.ProjectStatus;

import java.util.List;

/** No dedicated {@code GET /projects/{id}} endpoint exists in build-plan.md's feature 15 list —
 * {@code GET /api/v1/projects} is the only read path, so this response carries full detail
 * (nested {@code assets}/{@code challenges}), not a list-summary shape. {@code ProjectService}
 * bulk-fetches both across a whole page in two flat queries, never one query per project
 * (code-standards.md "N+1 Prevention"). */
public record ProjectResponse(
        Long id,
        String title,
        String slug,
        String description,
        List<String> techStack,
        ProjectDifficulty difficulty,
        String domain,
        String starterRepoUrl,
        String version,
        ProjectStatus status,
        String createdByUuid,
        String createdByFullName,
        List<ProjectAssetResponse> assets,
        List<ChallengeResponse> challenges
) {
}
