package com.moriah.skillhub.crm;

import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.crm.dto.AddLeadActivityRequest;
import com.moriah.skillhub.crm.dto.CreateLeadRequest;
import com.moriah.skillhub.crm.dto.LeadActivityResponse;
import com.moriah.skillhub.crm.dto.LeadResponse;
import com.moriah.skillhub.crm.dto.SalesTargetResponse;
import com.moriah.skillhub.crm.dto.UpdateLeadStatusRequest;
import com.moriah.skillhub.crm.entity.Lead;
import com.moriah.skillhub.crm.entity.LeadActivity;
import com.moriah.skillhub.crm.entity.LeadActivityType;
import com.moriah.skillhub.crm.entity.LeadSource;
import com.moriah.skillhub.crm.entity.LeadStatus;
import com.moriah.skillhub.crm.entity.SalesTarget;
import com.moriah.skillhub.crm.repository.LeadActivityRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

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

    private LeadResponse tryCreateNew(CreateLeadRequest request, String dedupeHash, Long callerUserId) {
        User agent = userRepository.getReferenceById(callerUserId);

        Lead lead = new Lead();
        lead.setName(request.name());
        lead.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        lead.setPhone(LeadDedupeHasher.normalizePhone(request.phone()));
        lead.setSource(request.source());
        lead.setLeadType(request.leadType());
        lead.setInstitution(request.institution());
        lead.setInterestedPlanId(request.interestedPlanId());
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
        leadRepository.save(lead);

        recordActivity(lead, userRepository.getReferenceById(callerUserId), LeadActivityType.NOTE,
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
                User convertedUser = userRepository.findByUuid(request.convertedUserUuid())
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, request.convertedUserUuid()));
                lead.setConvertedUser(convertedUser);
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
                lead.getStatus(),
                lead.getAssignedAgent() == null ? null : lead.getAssignedAgent().getUuid(),
                lead.getAssignedAgent() == null ? null : lead.getAssignedAgent().getFullName(),
                lead.getLostReason(),
                lead.getConvertedUser() == null ? null : lead.getConvertedUser().getUuid(),
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
