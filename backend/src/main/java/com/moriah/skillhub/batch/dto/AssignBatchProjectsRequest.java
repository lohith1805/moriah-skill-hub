package com.moriah.skillhub.batch.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** {@code PUT /api/v1/batches/{id}/projects} — wholesale replace, same "empty list clears
 * everything" convention {@code UpdateLeadCampaignRequest.leadIds} uses. Every id must be an
 * existing, {@code PUBLISHED} project whose {@code track} matches this batch's own {@code
 * trackCode} — {@code BatchService#assignProjects} validates both. */
public record AssignBatchProjectsRequest(
        @NotNull List<Long> projectIds
) {
}
