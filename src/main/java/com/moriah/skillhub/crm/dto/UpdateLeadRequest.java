package com.moriah.skillhub.crm.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * {@code PUT /api/v1/leads/{id}} — a partial edit of the mutable descriptive fields. Every field
 * is optional; a {@code null} means "leave unchanged".
 * <p>
 * Deliberately cannot touch {@code email}/{@code phone} (they form the dedupe identity — a
 * changed contact detail is a new lead, ingested via {@code POST /leads}), {@code status} (its
 * own {@code PUT /{id}/status} endpoint, with the pipeline rules), or {@code assignedAgent}
 * (auto-set to the ingesting agent, no reassignment endpoint in feature 18's scope).
 */
public record UpdateLeadRequest(
        @Size(max = 150) String name,
        @Size(max = 50) String leadType,
        @Size(max = 150) String institution,
        Long interestedPlanId,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal dealValue
) {
}
