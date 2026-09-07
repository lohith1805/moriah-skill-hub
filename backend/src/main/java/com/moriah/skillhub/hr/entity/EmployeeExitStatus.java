package com.moriah.skillhub.hr.entity;

/** Mirrors {@code chk_employee_exits_status} in {@code V28__employee_exits.sql}. {@code COMPLETED}
 * is terminal and is the only transition that touches {@code employees.status} /
 * {@code date_of_exit}. {@code CANCELLED} aborts an offboarding that was started in error. */
public enum EmployeeExitStatus {
    INITIATED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
