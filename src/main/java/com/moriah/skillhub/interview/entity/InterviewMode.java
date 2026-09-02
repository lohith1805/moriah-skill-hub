package com.moriah.skillhub.interview.entity;

/** Mirrors {@code chk_student_interviews_mode} in {@code V26__student_interviews.sql}. Nullable —
 * a scheduled interview need not have committed to online vs onsite yet. */
public enum InterviewMode {
    ONLINE,
    ONSITE
}
