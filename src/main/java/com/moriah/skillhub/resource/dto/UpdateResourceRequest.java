package com.moriah.skillhub.resource.dto;

import com.moriah.skillhub.resource.entity.ResourceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code PUT /api/v1/resources/{id}} (gap B1.6). Full-field replace, matching every other
 * {@code Update*Request}. {@code active} lets a curator hide a resource without deleting it;
 * {@code DELETE} is the shortcut for {@code active = false}. */
public record UpdateResourceRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull ResourceCategory category,
        @NotBlank @Size(max = 1000) String url,
        @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags,
        boolean active,
        @Size(max = 30) String track,
        Long projectId
) {
}
