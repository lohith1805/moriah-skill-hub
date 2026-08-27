package com.moriah.skillhub.sprint.entity;

/** Matches V6's {@code chk_tasks_status} CHECK constraint exactly. Forward-only state machine
 * (build-plan.md feature 11): {@code BACKLOG -> ASSIGNED -> IN_PROGRESS -> IN_REVIEW ->
 * COMPLETED | REJECTED}. {@code COMPLETED}/{@code REJECTED} are terminal — see
 * {@code TaskService.ALLOWED_TRANSITIONS}, the single source of truth every mutation path
 * (create/assign/pull/update) funnels through. */
public enum TaskStatus {
    BACKLOG,
    ASSIGNED,
    IN_PROGRESS,
    IN_REVIEW,
    COMPLETED,
    REJECTED
}
