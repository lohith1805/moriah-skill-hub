package com.moriah.skillhub.placement.repository;

import com.moriah.skillhub.placement.entity.Placement;
import com.moriah.skillhub.placement.entity.PlacementStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlacementRepository extends JpaRepository<Placement, Long> {

    boolean existsByRecruitmentRequestId(Long recruitmentRequestId);

    /** {@code GET /api/v1/placements/candidates} — every live shortlisting (a REJECTED one is not
     * an offer-letter target), newest activity first. Bounded by open client placements, so no
     * pagination. */
    List<Placement> findByStageNotOrderByUpdatedAtDesc(PlacementStage stage);

    /** {@code GET /api/v1/placements} — optional (candidateId, clientId, stage) filter, all
     * nullable. STUDENT passes their own id as {@code candidateId}, CLIENT their own as
     * {@code clientId}, HR/ADMIN pass neither. */
    @Query("""
            SELECT p FROM Placement p
            WHERE (:candidateId IS NULL OR p.candidateId = :candidateId)
              AND (:clientId IS NULL OR p.clientId = :clientId)
              AND (:stage IS NULL OR p.stage = :stage)
            """)
    Page<Placement> search(@Param("candidateId") Long candidateId,
                           @Param("clientId") Long clientId,
                           @Param("stage") PlacementStage stage,
                           Pageable pageable);
}
