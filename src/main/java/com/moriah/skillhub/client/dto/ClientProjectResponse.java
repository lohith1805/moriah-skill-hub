package com.moriah.skillhub.client.dto;

import com.moriah.skillhub.client.entity.ClientProjectStatus;

import java.time.Instant;

public record ClientProjectResponse(
        Long id,
        Long clientId,
        String clientName,
        String title,
        String scopeDescription,
        String budgetRange,
        String additionalNotes,
        Long targetBatchId,
        ClientProjectStatus status,
        String assignedBaUuid,
        String assignedBaName,
        String assignedDeveloperUuid,
        String assignedDeveloperName,
        Instant submittedAt
) {
}
