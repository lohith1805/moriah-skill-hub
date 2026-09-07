package com.moriah.skillhub.hr.entity;

/** Mirrors {@code chk_employee_onboardings_status} in {@code V29__employee_onboardings.sql}.
 * Unlike an exit, completing an onboarding has no side effect on the {@code employees} row. */
public enum OnboardingStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
