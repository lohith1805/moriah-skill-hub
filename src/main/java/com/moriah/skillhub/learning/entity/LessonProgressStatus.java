package com.moriah.skillhub.learning.entity;

/**
 * Mirrors {@code chk_lesson_progress_status} in {@code V22__video_lessons.sql}. There is no
 * {@code NOT_STARTED} row — the absence of a {@code lesson_progress} row means not started, and
 * the response DTO synthesises that value.
 */
public enum LessonProgressStatus {
    IN_PROGRESS,
    COMPLETED
}
