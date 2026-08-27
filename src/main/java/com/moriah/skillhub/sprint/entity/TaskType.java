package com.moriah.skillhub.sprint.entity;

/** Matches V6's {@code chk_tasks_type} CHECK constraint exactly (build-plan.md feature 11). */
public enum TaskType {
    DAILY,
    ASSIGNMENT,
    STORY,
    BUGFIX
}
