package com.moriah.skillhub.placement.dto;

import com.moriah.skillhub.placement.entity.PlacementStage;

import java.util.Map;

/**
 * {@code GET /api/v1/placements/candidates} — one option in the HR letter generator's
 * "Recipient" picker. Every row is a candidate a client has shortlisted (a {@code placements}
 * row exists), so HR chooses from real records instead of free-typing a name. {@code graduated}
 * is {@code true} when the candidate has reached {@code GRADUATED} in some batch — the FE can
 * surface / default to those. {@code details} is the placement's own JSON blob (track,
 * designation, ctc, …) so the picker can prefill the rest of the form.
 */
public record PlacementCandidateOption(
        Long placementId,
        String candidateUuid,
        String candidateName,
        String clientUuid,
        String clientContactName,
        PlacementStage stage,
        boolean graduated,
        Map<String, Object> details
) {
}
