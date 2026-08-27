package com.moriah.skillhub.submission.entity;

/** Matches V7's {@code chk_weekly_reviews_rating} CHECK constraint exactly. {@code
 * UNSATISFACTORY} is what feature 17's {@code REVIEW_FAILED} PIP rule counts — build-plan.md
 * feature 12: "This is the data source for the REVIEW_FAILED PIP rule." */
public enum WeeklyRating {
    SATISFACTORY,
    NEEDS_IMPROVEMENT,
    UNSATISFACTORY
}
