package com.moriah.skillhub.project.entity;

/** build-plan.md feature 15: {@code DRAFT -> PUBLISHED -> ARCHIVED}. Only {@code PUBLISHED}
 * attaches to a task. A version bump creates a new {@code DRAFT} row rather than mutating a
 * {@code PUBLISHED}/{@code ARCHIVED} one — see {@code ProjectService#update}. */
public enum ProjectStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
