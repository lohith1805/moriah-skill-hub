package com.moriah.skillhub.learning.dto;

import java.time.Instant;

/** One row of {@code GET /api/v1/lessons/me/progress} — the caller's progress on a lesson they
 * have started, with just enough of the lesson (title, module) to render a "continue watching"
 * list without a second call. */
public record MyLessonProgressItem(
        Long lessonId,
        String title,
        String moduleName,
        String status,
        int watchedSeconds,
        Integer durationSeconds,
        Instant completedAt,
        Instant updatedAt
) {
}
