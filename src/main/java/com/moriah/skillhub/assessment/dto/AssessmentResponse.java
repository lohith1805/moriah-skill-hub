package com.moriah.skillhub.assessment.dto;

public record AssessmentResponse(
        Long id,
        Long batchId,
        Long projectId,
        String title,
        Integer durationMinutes,
        Integer passPercentage,
        Integer maxAttempts,
        boolean active
) {
}
