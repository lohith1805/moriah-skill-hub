package com.moriah.skillhub.hr.entity;

/** {@code EXITED} is a clean/voluntary departure; {@code TERMINATED} is for-cause. {@code
 * HrLetterService}'s experience/relieving eligibility rule (build-plan.md feature 19) reads this
 * distinction directly: {@code EXITED} qualifies, {@code TERMINATED} never does. */
public enum EmployeeStatus {
    ACTIVE,
    EXITED,
    TERMINATED
}
