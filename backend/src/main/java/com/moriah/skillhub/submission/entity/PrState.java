package com.moriah.skillhub.submission.entity;

/** GitHub's own three PR states, verbatim — matches V7's {@code chk_task_submissions_pr_state}
 * CHECK constraint. Not spelled out in architecture.md; the GitHub REST API's {@code state}
 * field is what this stores, read directly off the response. */
public enum PrState {
    OPEN,
    CLOSED,
    MERGED
}
