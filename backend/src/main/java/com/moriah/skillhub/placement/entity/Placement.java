package com.moriah.skillhub.placement.entity;

import com.moriah.skillhub.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per APPROVED {@code recruitment_request} — auto-created by {@code TalentService.decide}.
 * {@code details} is a pre-serialized JSON object string (the FE's loose stage-field bag),
 * round-tripped by {@code PlacementService}; bare {@code candidateId}/{@code clientId} rather than
 * {@code @ManyToOne} so this module owns no {@code User} mapping (shared-kernel rule — names are
 * resolved in the service).
 */
@Entity
@Table(name = "placements")
@Getter
@Setter
@NoArgsConstructor
public class Placement extends BaseEntity {

    @Column(name = "recruitment_request_id", nullable = false)
    private Long recruitmentRequestId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlacementStage stage = PlacementStage.SHORTLISTED;

    @Column(columnDefinition = "json")
    private String details;
}
