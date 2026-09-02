package com.moriah.skillhub.interview.entity;

/** Mirrors {@code chk_student_interviews_status} in {@code V26__student_interviews.sql}. {@code
 * DELETE} moves an interview to {@code CANCELLED} rather than row-deleting it. */
public enum InterviewStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
