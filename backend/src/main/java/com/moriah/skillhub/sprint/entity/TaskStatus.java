package com.moriah.skillhub.sprint.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

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
    REJECTED;

    /** Not yet concluded — a task in one of these is still outstanding work for its assignee;
     * the two terminal states ({@code COMPLETED}, {@code REJECTED}) are excluded. Used by the
     * feature-20 graduation / certificate gate: a student with any assigned task still in this
     * set has unfinished sprint work and cannot be graduated or issued a certificate. */
    public static final Set<TaskStatus> UNFINISHED =
            Collections.unmodifiableSet(EnumSet.of(BACKLOG, ASSIGNED, IN_PROGRESS, IN_REVIEW));
}
