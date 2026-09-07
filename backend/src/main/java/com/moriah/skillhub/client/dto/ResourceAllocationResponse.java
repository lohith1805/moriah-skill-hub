package com.moriah.skillhub.client.dto;

import java.time.LocalDate;

public record ResourceAllocationResponse(
        Long id,
        Long clientProjectId,
        Long batchId,
        String userUuid,
        String userFullName,
        String roleInProject,
        Integer allocatedDays,
        Integer storyPointsEstimate,
        LocalDate fromDate,
        LocalDate toDate
) {
}
