package com.moriah.skillhub.assessment.dto;

import java.time.Instant;

/** A question bank (gap B1.15). {@code questionCount} is the number of items currently in it. */
public record QuestionBankResponse(
        Long id,
        String name,
        String topic,
        String description,
        boolean active,
        long questionCount,
        String createdByUuid,
        Instant createdAt,
        Instant updatedAt
) {
}
