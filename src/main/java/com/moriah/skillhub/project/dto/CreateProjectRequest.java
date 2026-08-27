package com.moriah.skillhub.project.dto;

import com.moriah.skillhub.project.entity.ProjectDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** build-plan.md feature 15: "DEVELOPER authors projects with tech stack, difficulty, domain,
 * starter repo, version." {@code slug} is server-generated from {@code title} (never
 * client-supplied — same "server decides the identifier" reasoning as every auto-incrementing id
 * in this codebase), always created {@code DRAFT} (build-plan.md's own state machine). */
public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        List<@NotBlank @Size(max = 50) String> techStack,
        ProjectDifficulty difficulty,
        @Size(max = 100) String domain,
        @Size(max = 500) String starterRepoUrl,
        @Size(max = 10) String version
) {
}
