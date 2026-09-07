package com.moriah.skillhub.crm.repository;

import com.moriah.skillhub.crm.entity.LeadCampaign;
import com.moriah.skillhub.crm.entity.LeadCampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadCampaignRepository extends JpaRepository<LeadCampaign, Long> {

    /** {@code GET /api/v1/leads/campaigns} — {@code status} optional, {@code null} lists every
     * campaign (the {@code UserRepository.search} nullable-parameter idiom). */
    @Query("SELECT c FROM LeadCampaign c WHERE (:status IS NULL OR c.status = :status)")
    Page<LeadCampaign> search(@Param("status") LeadCampaignStatus status, Pageable pageable);
}
