package com.moriah.skillhub.crm.repository;

import com.moriah.skillhub.crm.entity.LeadCampaignRecipient;
import com.moriah.skillhub.crm.entity.LeadCampaignRecipientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LeadCampaignRecipientRepository extends JpaRepository<LeadCampaignRecipient, LeadCampaignRecipientId> {

    @Query("SELECT r.id.leadId FROM LeadCampaignRecipient r WHERE r.id.campaignId = :campaignId")
    List<Long> findLeadIdsByCampaignId(@Param("campaignId") Long campaignId);

    /** {@code LeadCampaignService#list}'s batched per-page audience lookup — one flat query for a
     * whole {@code Page<LeadCampaign>} instead of one {@link #findLeadIdsByCampaignId} call per
     * row (code-standards.md "no repository call inside a loop"), same idiom {@code
     * AdminUserService.list}'s {@code findRoleCodesByUserIds} already establishes. */
    List<LeadCampaignRecipient> findByIdCampaignIdIn(Collection<Long> campaignIds);

    /** {@code LeadCampaignService#update}'s wholesale replace — same delete-then-insert idiom
     * {@code AdminUserService.updateRoles} uses for {@code user_roles}: no per-row metadata worth
     * diffing, so a full replace is simpler and no less correct. */
    void deleteByIdCampaignId(Long campaignId);
}
