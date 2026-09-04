package com.moriah.skillhub.resource.dto;

import com.moriah.skillhub.resource.entity.ResourceCategory;

import java.time.Instant;
import java.util.List;

/** One row of the Resource Library (gap B1.6). {@code tags} is always a list, never {@code null}
 * — an empty list where the row has none. {@code createdByUuid} is the curating user's public id. */
public record LearningResourceResponse(
        Long id,
        String title,
        String description,
        ResourceCategory category,
        String track,
        Long projectId,
        String url,
        List<String> tags,
        String createdByUuid,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
