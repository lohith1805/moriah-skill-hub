package com.moriah.skillhub.crm;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.crm.dto.CreateLeadCampaignRequest;
import com.moriah.skillhub.crm.dto.LeadCampaignResponse;
import com.moriah.skillhub.crm.dto.UpdateLeadCampaignRequest;
import com.moriah.skillhub.crm.entity.LeadCampaign;
import com.moriah.skillhub.crm.entity.LeadCampaignRecipient;
import com.moriah.skillhub.crm.entity.LeadCampaignStatus;
import com.moriah.skillhub.crm.repository.LeadCampaignRecipientRepository;
import com.moriah.skillhub.crm.repository.LeadCampaignRepository;
import com.moriah.skillhub.crm.repository.LeadRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lead-Gen Campaigns (gap B1.7). CRUD for {@code lead_campaigns}, {@code LEAD_GEN}/{@code ADMIN}
 * (gated on the controller). {@code UserRepository} injected directly to resolve creator uuids —
 * the same shared-identity-primitive access {@code LeadService} and every other module use.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadCampaignService {

    private final LeadCampaignRepository campaignRepository;
    private final LeadCampaignRecipientRepository recipientRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<LeadCampaignResponse> list(LeadCampaignStatus status, Pageable pageable) {
        var page = campaignRepository.search(status, pageable);
        Map<Long, String> creatorUuids = resolveCreatorUuids(page.getContent());
        List<Long> campaignIds = page.getContent().stream().map(LeadCampaign::getId).toList();
        Map<Long, List<Long>> leadIdsByCampaignId = campaignIds.isEmpty()
                ? Map.of()
                : recipientRepository.findByIdCampaignIdIn(campaignIds).stream()
                        .collect(Collectors.groupingBy(r -> r.getId().getCampaignId(),
                                Collectors.mapping(r -> r.getId().getLeadId(), Collectors.toList())));
        return PageResponse.from(page.map(c ->
                toResponse(c, leadIdsByCampaignId.getOrDefault(c.getId(), List.of()), creatorUuids)));
    }

    @Transactional
    public LeadCampaignResponse create(CreateLeadCampaignRequest request, Long callerUserId) {
        requireValidWindow(request.startDate(), request.endDate());
        List<Long> leadIds = requireValidLeadIds(request.leadIds());

        LeadCampaign campaign = new LeadCampaign();
        campaign.setName(request.name());
        campaign.setChannel(request.channel());
        campaign.setDescription(blankToNull(request.description()));
        campaign.setStartDate(request.startDate());
        campaign.setEndDate(request.endDate());
        campaign.setBudget(request.budget());
        campaign.setTargetLeads(request.targetLeads());
        campaign.setStatus(LeadCampaignStatus.PLANNED);
        campaign.setCreatedBy(callerUserId);
        campaignRepository.save(campaign);

        saveRecipients(campaign.getId(), leadIds);

        auditLogService.record(callerUserId, "LEAD_CAMPAIGN_CREATED", "LeadCampaign", campaign.getId(), null, campaign.getName());
        log.info("[leads/campaigns] {} created campaign {} ({}) with {} recipient(s)",
                callerUserId, campaign.getId(), campaign.getChannel(), leadIds.size());
        return toResponse(campaign, leadIds, resolveCreatorUuids(List.of(campaign)));
    }

    @Transactional
    public LeadCampaignResponse update(Long id, UpdateLeadCampaignRequest request, Long callerUserId) {
        LeadCampaign campaign = requireCampaign(id);
        requireValidWindow(request.startDate(), request.endDate());

        campaign.setName(request.name());
        campaign.setChannel(request.channel());
        campaign.setDescription(blankToNull(request.description()));
        campaign.setStartDate(request.startDate());
        campaign.setEndDate(request.endDate());
        campaign.setBudget(request.budget());
        campaign.setTargetLeads(request.targetLeads());
        campaign.setStatus(request.status());

        // null leadIds = "didn't touch the audience" (e.g. a status-only edit); a present list
        // (even empty) replaces the whole audience wholesale, same delete-then-insert idiom
        // AdminUserService.updateRoles uses for user_roles.
        List<Long> leadIds = request.leadIds() != null
                ? requireValidLeadIds(request.leadIds())
                : recipientRepository.findLeadIdsByCampaignId(id);
        if (request.leadIds() != null) {
            recipientRepository.deleteByIdCampaignId(id);
            saveRecipients(id, leadIds);
        }

        auditLogService.record(callerUserId, "LEAD_CAMPAIGN_UPDATED", "LeadCampaign", campaign.getId(), null, campaign.getName());
        return toResponse(campaign, leadIds, resolveCreatorUuids(List.of(campaign)));
    }

    /** {@code DELETE} — moves to {@code CANCELLED}, never row-deletes, so a lead attributed to
     * this campaign later still resolves. Idempotent. */
    @Transactional
    public void cancel(Long id, Long callerUserId) {
        LeadCampaign campaign = requireCampaign(id);
        if (campaign.getStatus() != LeadCampaignStatus.CANCELLED) {
            campaign.setStatus(LeadCampaignStatus.CANCELLED);
            auditLogService.record(callerUserId, "LEAD_CAMPAIGN_CANCELLED", "LeadCampaign", campaign.getId(), null, campaign.getName());
        }
    }

    private void requireValidWindow(LocalDate start, LocalDate end) {
        if (end != null && end.isBefore(start)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "endDate must not be before startDate.");
        }
    }

    /** De-dupes and validates every id actually resolves to a real lead — a bad id 404s the
     * whole request rather than silently dropping it (the same "fail loud on a bad reference"
     * choice {@code ResourceAllocationService.create} makes for its own batchId/userUuid checks). */
    private List<Long> requireValidLeadIds(List<Long> rawLeadIds) {
        if (rawLeadIds == null || rawLeadIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = rawLeadIds.stream().distinct().toList();
        long found = leadRepository.findAllById(distinct).stream().count();
        if (found != distinct.size()) {
            throw new ResourceNotFoundException(ErrorCode.LEAD_NOT_FOUND, distinct);
        }
        return distinct;
    }

    private void saveRecipients(Long campaignId, List<Long> leadIds) {
        if (leadIds.isEmpty()) {
            return;
        }
        recipientRepository.saveAll(leadIds.stream()
                .map(leadId -> new LeadCampaignRecipient(campaignId, leadId))
                .toList());
    }

    private LeadCampaign requireCampaign(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LEAD_CAMPAIGN_NOT_FOUND, id));
    }

    private Map<Long, String> resolveCreatorUuids(List<LeadCampaign> campaigns) {
        List<Long> ids = campaigns.stream().map(LeadCampaign::getCreatedBy).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getUuid));
    }

    private LeadCampaignResponse toResponse(LeadCampaign c, List<Long> leadIds, Map<Long, String> creatorUuids) {
        return new LeadCampaignResponse(
                c.getId(), c.getName(), c.getChannel(), c.getDescription(),
                c.getStartDate(), c.getEndDate(), c.getBudget(), c.getTargetLeads(),
                c.getStatus(), leadIds, creatorUuids.get(c.getCreatedBy()), c.getCreatedAt(), c.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
