package com.moriah.skillhub.project.dto;

import com.moriah.skillhub.project.entity.ProjectDifficulty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code PUT /api/v1/projects/{id}}. Every field optional and null-preserves the existing value
 * (same convention {@code StandupService#update}'s {@code notes}/{@code lateCutoffMinutes}
 * established) — applies whether {@code ProjectService#update} edits the row in place ({@code
 * DRAFT}) or copies it forward into a new version ({@code PUBLISHED}/{@code ARCHIVED}). {@code
 * version} is the one field that's conditionally required: mandatory when bumping a version,
 * unused when editing a still-{@code DRAFT} row in place — validated in the service, not here,
 * since which case applies depends on the target row's current status. */
public record UpdateProjectRequest(
        @Size(max = 200) String title,
        @Size(max = 5000) String description,
        List<@Size(max = 50) String> techStack,
        ProjectDifficulty difficulty,
        @Size(max = 100) String domain,
        @Size(max = 50) String track,
        @Size(max = 500) String starterRepoUrl,
        @Size(max = 10) String version
) {
}
