package com.moriah.skillhub.learning.dto;

import java.time.Instant;

/** Result of grading a lesson quiz submission (gap B1.4). {@code passed} = score/total >= 60%.
 * On a pass the lesson's progress is upserted to COMPLETED. */
public record LessonQuizResultResponse(
        int score,
        int total,
        boolean passed,
        int passMarkPercent,
        Instant submittedAt
) {
}
