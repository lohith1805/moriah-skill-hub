package com.moriah.skillhub.client.dto;

import com.moriah.skillhub.client.entity.ClientProjectStatus;

import java.time.Instant;

public record ClientProjectResponse(
        Long id,
        Long clientId,
        String title,
        String scopeDescription,
        String budgetRange,
        Long targetBatchId,
        ClientProjectStatus status,
        Instant submittedAt
) {
}
