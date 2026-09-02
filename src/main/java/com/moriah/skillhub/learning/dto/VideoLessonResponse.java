package com.moriah.skillhub.learning.dto;

import java.time.Instant;

/**
 * One lesson (gap B1.4). {@code progress} is the caller's own progress on this lesson and is
 * always non-null — {@code status = "NOT_STARTED"} when there is no progress row yet (including
 * on the create/update responses, where the caller has necessarily not watched it).
 */
public record VideoLessonResponse(
        Long id,
        String title,
        String description,
        String moduleName,
        String videoUrl,
        Integer durationSeconds,
        int sortOrder,
        boolean published,
        String createdByUuid,
        LessonProgressView progress,
        Instant createdAt,
        Instant updatedAt
) {
}
