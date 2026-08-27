package com.moriah.skillhub.pip.entity;

/** {@code TRIGGERED}/{@code IN_PROGRESS} are both "open" (V11's {@code open_user_id} generated
 * column) — {@code IN_PROGRESS} is reached the moment a PM completes the record's first milestone
 * (no separate endpoint exists to advance it; see {@code PipService#completeMilestone}), signalling
 * active remediation rather than an untouched trigger. {@code CLEARED}/{@code TERMINATED}/{@code
 * REASSIGNED} are the three review outcomes (build-plan.md feature 17), all terminal. */
public enum PipStatus {
    TRIGGERED,
    IN_PROGRESS,
    CLEARED,
    TERMINATED,
    REASSIGNED
}
