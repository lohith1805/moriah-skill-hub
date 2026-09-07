package com.moriah.skillhub.placement.dto;

import com.moriah.skillhub.placement.entity.PlacementStage;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * {@code stage} is the target stage (may equal the current one for a details-only update).
 * {@code details} is merged into the stored JSON object (keys with a {@code null} value are
 * removed); pass an empty map to leave it untouched.
 */
public record UpdatePlacementRequest(
        @NotNull PlacementStage stage,
        Map<String, Object> details
) {
}
