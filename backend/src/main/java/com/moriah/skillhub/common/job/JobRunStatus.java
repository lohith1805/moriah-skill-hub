package com.moriah.skillhub.common.job;

/** Mirrors the {@code chk_job_runs_status} CHECK constraint in {@code V2__system_audit_notifications.sql}. */
public enum JobRunStatus {
    RUNNING,
    SUCCESS,
    FAILED
}
