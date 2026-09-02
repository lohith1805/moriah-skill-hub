package com.moriah.skillhub.hr.entity;

/**
 * Mirrors {@code chk_employee_exits_type} in {@code V28__employee_exits.sql}. Only
 * {@link #TERMINATION} maps the employee to {@link EmployeeStatus#TERMINATED} on completion;
 * every other type maps to {@link EmployeeStatus#EXITED} — the distinction {@code HrLetterService}
 * already keys the relieving/experience-letter eligibility off.
 */
public enum ExitType {
    RESIGNATION,
    TERMINATION,
    RETIREMENT,
    CONTRACT_END,
    OTHER
}
