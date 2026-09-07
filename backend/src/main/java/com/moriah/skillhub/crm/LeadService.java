package com.moriah.skillhub.crm;

import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.crm.dto.AddLeadActivityRequest;
import com.moriah.skillhub.crm.dto.CreateLeadRequest;
import com.moriah.skillhub.crm.dto.InboundLeadRequest;
import com.moriah.skillhub.crm.dto.LeadActivityResponse;
import com.moriah.skillhub.crm.dto.LeadResponse;
import com.moriah.skillhub.crm.dto.SalesLeaderboardRowResponse;
import com.moriah.skillhub.crm.dto.SalesTargetResponse;
import com.moriah.skillhub.crm.dto.UpdateLeadRequest;
import com.moriah.skillhub.crm.dto.UpdateLeadStatusRequest;
import com.moriah.skillhub.crm.entity.Lead;
import com.moriah.skillhub.crm.entity.LeadActivity;
import com.moriah.skillhub.crm.entity.LeadActivityType;
import com.moriah.skillhub.crm.entity.LeadSource;
import com.moriah.skillhub.crm.entity.LeadStatus;
import com.moriah.skillhub.crm.entity.SalesTarget;
import com.moriah.skillhub.crm.repository.LeadActivityRepository;
import com.moriah.skillhub.crm.repository.LeadAgentStatsView;
import com.moriah.skillhub.crm.repository.LeadRepository;
import com.moriah.skillhub.crm.repository.SalesTargetRepository;
import com.moriah.skillhub.subscription.repository.SubscriptionPlanRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * build-plan.md feature 18. Pipeline order and the forward-skip/backward-with-reason rule are
 * documented on {@link LeadStatus} itself. {@code sales_targets} rows have no create/update
 * endpoint anywhere in this feature's 6-endpoint list — {@link #myTargets} is read-only, the same
 * "table exists in schema, not yet API-managed" treatment {@code permissions}/{@code
 * role_permissions} already got (code-standards.md's Database Rules section) — see
 * progress-tracker.md's feature 18 decision log for why this wasn't widened beyond scope.
 * <p>
 * Every {@code users.id} this class would otherwise expose is resolved to/from {@code uuid}
 * instead (architecture.md: "No endpoint exposes users.id") — {@code agentUuid}/{@code
 * convertedUserUuid} in, {@code assignedAgentUuid}/{@code convertedUserUuid}/{@code agentUuid}
 * out.
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final SalesTargetRepository salesTargetRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final LeadWriter leadWriter;
    private final LeadWhatsAppSender leadWhatsAppSender;

    /** Never matches a real row (`assigned_agent_id` is `BIGINT UNSIGNED`) — an unrecognised
     * {@code agentUuid} filter should return an empty page, not silently ignore the filter. */
    private static final Long NO_SUCH_AGENT_ID = -1L;

    /** {@code callerUserId} is the ingesting agent, or {@code null} for an anonymous
     * landing-page submission ({@code POST /api/v1/leads/inbound}) — an unassigned lead a
     * lead-gen agent picks up later. */
    @Transactional
    public LeadResponse create(CreateLeadRequest request, Long callerUserId) {
        if (request.interestedPlanId() != null && !subscriptionPlanRepository.existsById(request.interestedPlanId())) {
            throw new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, request.interestedPlanId());
        }

        String dedupeHash = LeadDedupeHasher.hash(request.email(), request.phone());

        return leadRepository.findByDedupeHash(dedupeHash)
                .map(existing -> updateExisting(existing, request, callerUserId))
                .orElseGet(() -> tryCreateNew(request, dedupeHash, callerUserId));
    }

    /** {@code POST /api/v1/leads/inbound} — public, unauthenticated. Same dedupe-upsert as
     * {@link #create}, with no assigned agent and any {@code message} logged as a NOTE. */
    @Transactional
    public LeadResponse ingestInbound(InboundLeadRequest request) {
        CreateLeadRequest cr = new CreateLeadRequest(
                request.name(), request.email(), request.phone(),
                request.source() != null ? request.source() : LeadSource.LANDING_PAGE,
                request.leadType() != null && !request.leadType().isBlank() ? request.leadType() : "B2C",
                null, null, null);
        LeadResponse lead = create(cr, null);
        if (request.message() != null && !request.message().isBlank()) {
            recordActivity(leadRepository.getReferenceById(lead.id()), null, LeadActivityType.NOTE,
                    "INBOUND_MESSAGE", request.message().trim(), null, Instant.now());
        }
        return lead;
    }

    private LeadResponse tryCreateNew(CreateLeadRequest request, String dedupeHash, Long callerUserId) {
        User agent = callerUserId == null ? null : userRepository.getReferenceById(callerUserId);

        Lead lead = new Lead();
        lead.setName(request.name());
        lead.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        lead.setPhone(LeadDedupeHasher.normalizePhone(request.phone()));
        lead.setSource(request.source());
        lead.setLeadType(request.leadType());
        lead.setInstitution(request.institution());
        lead.setInterestedPlanId(request.interestedPlanId());
        lead.setDealValue(request.dealValue());
        lead.setStatus(LeadStatus.NEW);
        lead.setAssignedAgent(agent);
        lead.setDedupeHash(dedupeHash);

        return leadWriter.tryCreate(lead)
                .map(created -> {
                    recordActivity(created, agent, LeadActivityType.NOTE, "LEAD_CREATED",
                            "Ingested via source=" + request.source() + ", leadType=" + request.leadType(),
                            null, Instant.now());
                    return toResponse(created);
                })
                // Lost the create race to a concurrent identical submission — the winner's row now
                // exists under this exact dedupe_hash. leadWriter.findByDedupeHash (not
                // leadRepository's, REQUIRES_NEW) is deliberate: MySQL/InnoDB's REPEATABLE READ
                // snapshot was fixed at create()'s first (empty) read of this same transaction, so
                // re-running that read here still can't see the winner's just-committed row — a
                // fresh transaction is required to get a fresh snapshot (see LeadWriter's Javadoc).
                .orElseGet(() -> updateExisting(
                        leadWriter.findByDedupeHash(dedupeHash)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Lead insert failed on a duplicate key but no row exists for hash " + dedupeHash)),
                        request, callerUserId));
    }

    private LeadResponse updateExisting(Lead lead, CreateLeadRequest request, Long callerUserId) {
        lead.setName(request.name());
        lead.setSource(request.source());
        lead.setLeadType(request.leadType());
        lead.setInstitution(request.institution());
        lead.setInterestedPlanId(request.interestedPlanId());
        if (request.dealValue() != null) {
            lead.setDealValue(request.dealValue());
        }
        leadRepository.save(lead);

        User actor = callerUserId == null ? null : userRepository.getReferenceById(callerUserId);
        recordActivity(lead, actor, LeadActivityType.NOTE,
                "DUPLICATE_SUBMISSION",
                "Re-submitted via source=" + request.source() + ", leadType=" + request.leadType(),
                null, Instant.now());

        return toResponse(lead);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> list(LeadStatus status, String agentUuid, LeadSource source, Pageable pageable) {
        Long agentId = agentUuid == null ? null
                : userRepository.findByUuid(agentUuid).map(User::getId).orElse(NO_SUCH_AGENT_ID);
        Page<Lead> page = leadRepository.search(status, agentId, source, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    /** {@code GET /api/v1/leads/{id}}. An archived lead is treated as gone — a 404, same as one
     * that never existed (mirrors the soft-delete convention {@code lead_campaigns} established). */
    @Transactional(readOnly = true)
    public LeadResponse get(Long leadId) {
        return toResponse(requireActiveLead(leadId));
    }

    /** {@code PUT /api/v1/leads/{id}} — partial edit of the descriptive fields. {@code null}
     * fields are left untouched; {@code email}/{@code phone}/{@code status}/{@code assignedAgent}
     * are out of scope here ({@link UpdateLeadRequest} Javadoc). */
    @Transactional
    public LeadResponse update(Long leadId, UpdateLeadRequest request, Long callerUserId) {
        Lead lead = requireActiveLead(leadId);
        if (request.interestedPlanId() != null && !subscriptionPlanRepository.existsById(request.interestedPlanId())) {
            throw new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, request.interestedPlanId());
        }
        if (request.name() != null) {
            lead.setName(request.name());
        }
        if (request.leadType() != null) {
            lead.setLeadType(request.leadType());
        }
        if (request.institution() != null) {
            lead.setInstitution(request.institution());
        }
        if (request.interestedPlanId() != null) {
            lead.setInterestedPlanId(request.interestedPlanId());
        }
        if (request.dealValue() != null) {
            lead.setDealValue(request.dealValue());
        }
        leadRepository.save(lead);

        recordActivity(lead, userRepository.getReferenceById(callerUserId), LeadActivityType.NOTE,
                "LEAD_UPDATED", "Lead details edited.", null, Instant.now());

        return toResponse(lead);
    }

    @Transactional
    public LeadResponse updateStatus(Long leadId, UpdateLeadStatusRequest request, Long callerUserId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LEAD_NOT_FOUND, leadId));

        LeadStatus current = lead.getStatus();
        LeadStatus target = request.newStatus();

        if (current == LeadStatus.ENROLLED || current == LeadStatus.LOST) {
            throw new BusinessException(ErrorCode.LEAD_ALREADY_TERMINAL);
        }
        if (target == current) {
            throw new BusinessException(ErrorCode.LEAD_STATUS_UNCHANGED);
        }

        String note;
        if (target == LeadStatus.LOST) {
            lead.setLostReason(request.lostReason());
            note = request.lostReason();
        } else {
            int currentIndex = current.ordinal();
            int targetIndex = target.ordinal();
            if (targetIndex > currentIndex + 1) {
                throw new BusinessException(ErrorCode.LEAD_PIPELINE_SKIP);
            }
            boolean backward = targetIndex < currentIndex;
            if (backward && (request.reason() == null || request.reason().isBlank())) {
                throw new BusinessException(ErrorCode.LEAD_BACKWARD_REASON_REQUIRED);
            }
            if (target == LeadStatus.ENROLLED) {
                lead.setConvertedUser(resolveConvertedUser(request));
            }
            note = request.reason();
        }

        lead.setStatus(target);
        leadRepository.save(lead);

        recordActivity(lead, userRepository.getReferenceById(callerUserId), LeadActivityType.STATUS_CHANGE,
                current + " -> " + target, note, null, Instant.now());

        return toResponse(lead);
    }

    @Transactional
    public LeadActivityResponse addActivity(Long leadId, AddLeadActivityRequest request, Long callerUserId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LEAD_NOT_FOUND, leadId));

        LeadActivity activity = recordActivity(lead, userRepository.getReferenceById(callerUserId),
                request.activityType(), request.outcome(), request.notes(),
                request.nextFollowUpAt(), request.occurredAt());

        // Denormalised board-view cache — the furthest-out follow-up wins, so a later activity
        // that clears its follow-up (null) never wipes an earlier scheduled one.
        if (request.nextFollowUpAt() != null
                && (lead.getNextFollowUpAt() == null || request.nextFollowUpAt().isAfter(lead.getNextFollowUpAt()))) {
            lead.setNextFollowUpAt(request.nextFollowUpAt());
            leadRepository.save(lead);
        }

        if (request.activityType() == LeadActivityType.WHATSAPP) {
            leadWhatsAppSender.sendAfterCommit(request.templateCode(), lead.getPhone());
        }

        return toActivityResponse(activity);
    }

    @Transactional(readOnly = true)
    public SalesTargetResponse myTargets(Long callerUserId) {
        LocalDate currentMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        SalesTarget target = salesTargetRepository.findByAgentIdAndPeriodMonth(callerUserId, currentMonth)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SALES_TARGET_NOT_FOUND, callerUserId));
        return toTargetResponse(target);
    }

    /** {@code GET /api/v1/leads/{id}/activities} — this lead's full interaction history, newest
     * first. */
    @Transactional(readOnly = true)
    public PageResponse<LeadActivityResponse> listActivities(Long leadId, Pageable pageable) {
        requireActiveLead(leadId);
        return PageResponse.from(leadActivityRepository.findByLeadId(leadId, pageable).map(this::toActivityResponse));
    }

    /** {@code DELETE /api/v1/leads/{id}} — soft delete. The row stays (activity history, funnel
     * view, dedupe hash all keep referencing it); it just drops out of every list and the
     * leaderboard. */
    @Transactional
    public void archive(Long leadId, Long callerUserId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LEAD_NOT_FOUND, leadId));
        if (lead.getArchivedAt() != null) {
            throw new BusinessException(ErrorCode.LEAD_ALREADY_ARCHIVED);
        }
        lead.setArchivedAt(Instant.now());
        leadRepository.save(lead);
        recordActivity(lead, userRepository.getReferenceById(callerUserId), LeadActivityType.NOTE,
                "LEAD_ARCHIVED", "Lead archived by agent.", null, Instant.now());
    }

    /** {@code GET /api/v1/leads/targets/leaderboard} — per-agent standings for the current month.
     * Lead counts / pipeline value are live from {@code leads}; calls-made / quota columns come
     * from that agent's {@code sales_targets} row when one exists. */
    @Transactional(readOnly = true)
    public List<SalesLeaderboardRowResponse> leaderboard() {
        LocalDate currentMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        Map<Long, SalesTarget> targetsByAgent = salesTargetRepository.findByPeriodMonth(currentMonth).stream()
                .collect(Collectors.toMap(t -> t.getAgent().getId(), Function.identity()));

        return leadRepository.agentStats().stream()
                .map(row -> toLeaderboardRow(row, targetsByAgent.get(row.getAgentId())))
                .toList();
    }

    private SalesLeaderboardRowResponse toLeaderboardRow(LeadAgentStatsView stats, SalesTarget target) {
        return new SalesLeaderboardRowResponse(
                stats.getAgentUuid(),
                stats.getAgentName(),
                stats.getTotalLeads(),
                stats.getConverted(),
                stats.getPipelineValue() == null ? BigDecimal.ZERO : stats.getPipelineValue(),
                target == null ? null : target.getCallsTarget(),
                target == null ? null : target.getCallsMade(),
                target == null ? null : target.getConversionsTarget(),
                target == null ? null : target.getConversionsMade(),
                target == null ? null : target.getRevenueTarget(),
                target == null ? null : target.getRevenueAchieved());
    }

    /** The converted student for an ENROLLED move — by uuid if given, else by email (the identifier
     * a lead-gen agent actually has). {@link UpdateLeadStatusRequest} already guaranteed one is set. */
    private User resolveConvertedUser(UpdateLeadStatusRequest request) {
        if (request.convertedUserUuid() != null && !request.convertedUserUuid().isBlank()) {
            return userRepository.findByUuid(request.convertedUserUuid())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, request.convertedUserUuid()));
        }
        String email = request.convertedUserEmail().trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, email));
    }

    /** A lead that exists and has not been soft-deleted. */
    private Lead requireActiveLead(Long leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LEAD_NOT_FOUND, leadId));
        if (lead.getArchivedAt() != null) {
            throw new ResourceNotFoundException(ErrorCode.LEAD_NOT_FOUND, leadId);
        }
        return lead;
    }

    private LeadActivity recordActivity(Lead lead, User agent, LeadActivityType type, String outcome,
                                         String notes, Instant nextFollowUpAt, Instant occurredAt) {
        LeadActivity activity = new LeadActivity();
        activity.setLead(lead);
        activity.setAgent(agent);
        activity.setActivityType(type);
        activity.setOutcome(outcome);
        activity.setNotes(notes);
        activity.setNextFollowUpAt(nextFollowUpAt);
        activity.setOccurredAt(occurredAt);
        leadActivityRepository.save(activity);
        return activity;
    }

    private LeadResponse toResponse(Lead lead) {
        return new LeadResponse(
                lead.getId(),
                lead.getName(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getSource(),
                lead.getLeadType(),
                lead.getInstitution(),
                lead.getInterestedPlanId(),
                lead.getDealValue(),
                lead.getStatus(),
                lead.getAssignedAgent() == null ? null : lead.getAssignedAgent().getUuid(),
                lead.getAssignedAgent() == null ? null : lead.getAssignedAgent().getFullName(),
                lead.getLostReason(),
                lead.getConvertedUser() == null ? null : lead.getConvertedUser().getUuid(),
                lead.getNextFollowUpAt(),
                lead.getCreatedAt(),
                lead.getUpdatedAt());
    }

    private LeadActivityResponse toActivityResponse(LeadActivity activity) {
        return new LeadActivityResponse(
                activity.getId(),
                activity.getLead().getId(),
                activity.getAgent() == null ? null : activity.getAgent().getUuid(),
                activity.getAgent() == null ? null : activity.getAgent().getFullName(),
                activity.getActivityType(),
                activity.getOutcome(),
                activity.getNotes(),
                activity.getNextFollowUpAt(),
                activity.getOccurredAt());
    }

    private SalesTargetResponse toTargetResponse(SalesTarget target) {
        return new SalesTargetResponse(
                target.getId(),
                target.getAgent().getUuid(),
                target.getPeriodMonth(),
                target.getCallsTarget(),
                target.getCallsMade(),
                target.getConversionsTarget(),
                target.getConversionsMade(),
                target.getRevenueTarget(),
                target.getRevenueAchieved());
    }
}
