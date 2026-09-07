package com.moriah.skillhub.placement.dto;

import com.moriah.skillhub.placement.entity.PlacementStage;

import java.time.Instant;
import java.util.Map;

public record PlacementResponse(
        Long id,
        Long recruitmentRequestId,
        String candidateUuid,
        String candidateName,
        String clientUuid,
        String clientName,
        PlacementStage stage,
        Map<String, Object> details,
        Instant createdAt,
        Instant updatedAt
) {
}
