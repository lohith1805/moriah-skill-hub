package com.moriah.skillhub.resource.dto;

import com.moriah.skillhub.resource.entity.ResourceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code POST /api/v1/resources} (gap B1.6). Link-only — {@code url} is mandatory. {@code tags}
 * is an optional list of short labels the FE renders as chips and can filter on client-side. */
public record CreateResourceRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull ResourceCategory category,
        @NotBlank @Size(max = 1000) String url,
        @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags,
        @Size(max = 30) String track
) {
}
