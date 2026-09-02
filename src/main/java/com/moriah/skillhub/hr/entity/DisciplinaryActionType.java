package com.moriah.skillhub.hr.entity;

/** Mirrors {@code chk_disciplinary_actions_type} in {@code V30__disciplinary_actions.sql}. A
 * {@link #TERMINATION_RECOMMENDATION} is only a recommendation — the actual termination goes
 * through the exit flow, this never touches {@code employees.status}. */
public enum DisciplinaryActionType {
    VERBAL_WARNING,
    WRITTEN_WARNING,
    PIP,
    SUSPENSION,
    TERMINATION_RECOMMENDATION,
    OTHER
}
