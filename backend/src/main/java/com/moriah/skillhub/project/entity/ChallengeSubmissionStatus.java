package com.moriah.skillhub.project.entity;

/** Lifecycle of a {@link ChallengeSubmission}: freshly submitted, then a reviewer marks it
 * {@code ACCEPTED} or {@code NEEDS_WORK}. A student may resubmit after {@code NEEDS_WORK} —
 * each attempt is its own row. */
public enum ChallengeSubmissionStatus {
    SUBMITTED,
    ACCEPTED,
    NEEDS_WORK
}
