package com.moriah.skillhub.hr.entity;

/** Mirrors {@code chk_disciplinary_actions_status} in {@code V30__disciplinary_actions.sql}. */
public enum DisciplinaryStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    ESCALATED
}
