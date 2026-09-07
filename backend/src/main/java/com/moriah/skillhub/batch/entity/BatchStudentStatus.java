package com.moriah.skillhub.batch.entity;

/** Matches V6's {@code chk_batch_students_status} CHECK constraint exactly. {@code REASSIGNED}
 * is the terminal state for both a PM-driven removal and a payment refund — never a delete
 * (build-plan.md feature 10: "history is needed for PIP and certificates"). */
public enum BatchStudentStatus {
    ACTIVE,
    ON_PIP,
    GRADUATED,
    TERMINATED,
    REASSIGNED
}
