package com.moriah.skillhub.project.entity;

/** Shared by {@link Project} and {@link BugChallenge} — V8's own {@code chk_projects_difficulty}/
 * {@code chk_bug_challenges_difficulty} CHECK constraints list the identical three values. */
public enum ProjectDifficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}
