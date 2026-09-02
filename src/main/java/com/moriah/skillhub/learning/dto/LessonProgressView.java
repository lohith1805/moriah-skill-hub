package com.moriah.skillhub.learning.dto;

import com.moriah.skillhub.learning.entity.LessonProgressStatus;

import java.time.Instant;

/**
 * The caller's progress on one lesson, nested inside {@link VideoLessonResponse}. When the user
 * has never started the lesson this is still non-null: {@code status = NOT_STARTED},
 * {@code watchedSeconds = 0}. {@code NOT_STARTED} is a synthetic value not stored in the DB —
 * see {@link LessonProgressStatus}.
 */
public record LessonProgressView(
        String status,
        int watchedSeconds,
        Instant completedAt
) {

    public static final String NOT_STARTED = "NOT_STARTED";

    public static LessonProgressView notStarted() {
        return new LessonProgressView(NOT_STARTED, 0, null);
    }
}
