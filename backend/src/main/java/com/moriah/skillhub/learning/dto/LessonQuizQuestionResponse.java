package com.moriah.skillhub.learning.dto;

import java.util.List;

/** One quiz question as shown to a learner (gap B1.4). {@code correctIndex} is deliberately
 * absent — the key never leaves the service layer. */
public record LessonQuizQuestionResponse(
        Long id,
        String questionText,
        List<String> options,
        int sortOrder
) {
}
