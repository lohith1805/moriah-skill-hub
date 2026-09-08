package com.moriah.skillhub.client;

import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.entity.RequirementDocument;
import com.moriah.skillhub.client.entity.RequirementDocumentApproval;
import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.client.entity.RequirementDocumentType;
import com.moriah.skillhub.client.dto.PendingApprovalResponse;
import com.moriah.skillhub.client.repository.ClientProjectRepository;
import com.moriah.skillhub.client.repository.RequirementDocumentApprovalRepository;
import com.moriah.skillhub.client.repository.RequirementDocumentRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import com.moriah.skillhub.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Multi-party sign-off on a {@link RequirementDocument} — the redesign of the old single-approver
 * {@code RequirementDocumentService#approve}, which flipped {@code IN_REVIEW -> APPROVED} on one
 * BA click with no CLIENT or DEVELOPER visibility at all. Now every document gets one required
 * "slot" per {@link #REQUIRED_ROLES}, and only reaches {@code APPROVED} once every slot is filled
 * — see {@code RequirementDocumentApproval}'s own Javadoc for the slot model. The BA slot may be
 * filled by the document's own author (product decision — the CLIENT and DEVELOPER slots are the
 * real outside checks; a second-BA requirement just stalls docs on a small team).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequirementDocumentApprovalService {

    /** BRD/FRS need every party's sign-off (client scope, BA authoring, developer build
     * commitment); SRS/USER_STORY are internal/technical specs the client doesn't need to review —
     * user's own call: "this approve button ... is not necessary for all docs like after BRS and
     * FRS remaining docs just need to be approved by developer and BA." */
    private static final Map<RequirementDocumentType, List<RoleCode>> REQUIRED_ROLES = Map.of(
            RequirementDocumentType.BRD, List.of(RoleCode.CLIENT, RoleCode.BUSINESS_ANALYST, RoleCode.DEVELOPER),
            RequirementDocumentType.FRS, List.of(RoleCode.CLIENT, RoleCode.BUSINESS_ANALYST, RoleCode.DEVELOPER),
            RequirementDocumentType.SRS, List.of(RoleCode.BUSINESS_ANALYST, RoleCode.DEVELOPER),
            RequirementDocumentType.USER_STORY, List.of(RoleCode.BUSINESS_ANALYST, RoleCode.DEVELOPER));

    private static final List<RequirementDocumentType> DEVELOPER_TRIGGER_DOC_TYPES =
            List.of(RequirementDocumentType.BRD, RequirementDocumentType.FRS);

    private final RequirementDocumentApprovalRepository approvalRepository;
    private final RequirementDocumentRepository requirementDocumentRepository;
    private final ClientProjectRepository clientProjectRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final StaffAssignmentService staffAssignmentService;
    private final AuditLogService auditLogService;

    /** Called once, right after {@code RequirementDocumentService#create} first saves a new
     * document — pre-creates one pending slot per role {@link #REQUIRED_ROLES} says this
     * {@code docType} needs. Returns the saved rows so the caller can build its response without
     * a redundant read-back query. */
    @Transactional
    public List<RequirementDocumentApproval> createSlots(RequirementDocument document) {
        List<RoleCode> roles = REQUIRED_ROLES.getOrDefault(document.getDocType(),
                List.of(RoleCode.BUSINESS_ANALYST, RoleCode.DEVELOPER));
        List<RequirementDocumentApproval> slots = new ArrayList<>(roles.size());
        for (RoleCode role : roles) {
            slots.add(approvalRepository.save(new RequirementDocumentApproval(document, role)));
        }
        return slots;
    }

    /**
     * Infers which slot the caller may act on and fills it — see the class Javadoc for the
     * decision order. ADMIN is the one exception: it fast-tracks every still-pending slot in a
     * single call rather than one slot per click, since an admin override is meant to unblock a
     * stuck document immediately, not to be clicked once per missing party.
     *
     * <p>A BUSINESS_ANALYST slot being filled for the first time on a BRD/FRS (by anyone —
     * a real BA or an ADMIN override) auto-assigns the least-busy active developer to the
     * project, exactly once, if one isn't already assigned (see {@link StaffAssignmentService}).
     * Once every slot on the document is filled, {@code status} flips to {@code APPROVED}.
     */
    @Transactional
    public RequirementDocument approve(Long documentId, Long callerUserId) {
        RequirementDocument document = requireDocument(documentId);
        if (document.getStatus() == RequirementDocumentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This document has already been approved.");
        }

        User caller = userRepository.findById(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, callerUserId));
        List<RequirementDocumentApproval> slots = approvalRepository.findByDocumentId(documentId);
        ClientProject project = document.getClientProject();
        boolean hadPriorBaApproval = project != null
                && approvalRepository.existsPriorBusinessAnalystApproval(project.getId());

        List<String> callerRoles = SecurityUtils.currentUserRoles();
        Instant now = Instant.now();
        List<RequirementDocumentApproval> approvedNow = new ArrayList<>();

        if (callerRoles.contains(RoleCode.ADMIN.name())) {
            for (RequirementDocumentApproval slot : slots) {
                if (slot.getApprovedBy() == null) {
                    slot.setApprovedBy(caller);
                    slot.setApprovedAt(now);
                    approvedNow.add(slot);
                }
            }
            if (approvedNow.isEmpty()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This document has already been approved.");
            }
        } else {
            RequirementDocumentApproval slot = resolveCallerSlot(document, project, slots, caller);
            if (slot.getApprovedBy() != null) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "The " + slot.getApproverRole() + " sign-off for this document is already recorded.");
            }
            slot.setApprovedBy(caller);
            slot.setApprovedAt(now);
            approvedNow.add(slot);
        }
        approvalRepository.saveAll(approvedNow);

        for (RequirementDocumentApproval slot : approvedNow) {
            auditLogService.recordAfterCommit(callerUserId, "REQUIREMENT_DOCUMENT_APPROVAL_RECORDED",
                    "RequirementDocument", document.getId(), null,
                    Map.of("role", slot.getApproverRole().name(), "version", document.getVersion()));
        }

        boolean baJustApproved = approvedNow.stream().anyMatch(s -> s.getApproverRole() == RoleCode.BUSINESS_ANALYST);
        if (baJustApproved && !hadPriorBaApproval && project != null && project.getAssignedDeveloper() == null
                && DEVELOPER_TRIGGER_DOC_TYPES.contains(document.getDocType())) {
            staffAssignmentService.pickLeastBusy(RoleCode.DEVELOPER).ifPresent(dev -> {
                project.setAssignedDeveloper(dev);
                clientProjectRepository.save(project);
                log.info("[requirement-documents] auto-assigned developer {} to client project {} "
                        + "on first BA sign-off of {}", dev.getId(), project.getId(), document.getDocType());
            });
        }

        boolean allApproved = slots.stream().allMatch(s -> s.getApprovedBy() != null);
        if (allApproved) {
            document.setStatus(RequirementDocumentStatus.APPROVED);
            document.setApprovedBy(caller);
            requirementDocumentRepository.save(document);
            log.info("[requirement-documents] document {} v{} fully approved by all required parties",
                    document.getId(), document.getVersion());
        }

        return document;
    }

    /**
     * The other outcome {@link #approve} always lacked: any one required party can reject a
     * still-{@code IN_REVIEW} document outright, with a reason, instead of either signing off or
     * leaving it stuck pending forever with no way to say what's wrong. Unlike {@code approve},
     * this needs no per-slot bookkeeping — a reject from any single party kills the whole
     * document version immediately, regardless of how many other slots were already filled.
     * Existing approvals on this now-{@code REJECTED} version are left exactly as they are (a
     * true record of who'd already signed off before the rejection); they're not reset, because
     * the author's fix is a brand new version via {@code RequirementDocumentService#create} with
     * its own fresh slots, never an edit-in-place of this one. Same role inference as {@link
     * #resolveCallerSlot} (reused directly) — whoever can approve a slot can reject the whole
     * document; ADMIN can reject unconditionally, same override reach as its approve fast-track.
     */
    @Transactional
    public RequirementDocument reject(Long documentId, Long callerUserId, String reason) {
        RequirementDocument document = requireDocument(documentId);
        if (document.getStatus() == RequirementDocumentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This document has already been approved.");
        }
        if (document.getStatus() == RequirementDocumentStatus.REJECTED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This document has already been rejected.");
        }

        User caller = userRepository.findById(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, callerUserId));
        List<String> callerRoles = SecurityUtils.currentUserRoles();

        if (!callerRoles.contains(RoleCode.ADMIN.name())) {
            ClientProject project = document.getClientProject();
            List<RequirementDocumentApproval> slots = approvalRepository.findByDocumentId(documentId);
            // Authorization + role inference only — reject doesn't fill or care about a slot's
            // own approved/pending state, so the slot resolveCallerSlot returns is discarded.
            resolveCallerSlot(document, project, slots, caller);
        }

        document.setStatus(RequirementDocumentStatus.REJECTED);
        document.setRejectedBy(caller);
        document.setRejectedAt(Instant.now());
        document.setRejectionReason(reason);
        requirementDocumentRepository.save(document);

        auditLogService.recordAfterCommit(callerUserId, "REQUIREMENT_DOCUMENT_REJECTED", "RequirementDocument",
                document.getId(), null, Map.of("version", document.getVersion(), "reason", reason));
        log.info("[requirement-documents] {} rejected document {} v{}: {}",
                callerUserId, document.getId(), document.getVersion(), reason);

        return document;
    }

    /** Powers {@code GET /api/v1/requirement-documents/pending-my-approval} — every still-open
     * document where the caller's own role slot would resolve. ADMIN is deliberately excluded:
     * admins already have unrestricted {@code /ba/documents}/{@code /dev/requirement-documents}
     * oversight lists, and "everything is pending for admin" would make this inbox meaningless
     * for them. */
    @Transactional(readOnly = true)
    public List<PendingApprovalResponse> pendingFor(Long callerUserId) {
        User caller = userRepository.findById(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, callerUserId));
        List<RequirementDocumentApproval> results = new ArrayList<>();

        for (RequirementDocumentApproval slot : approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.CLIENT)) {
            ClientProject project = slot.getDocument().getClientProject();
            if (project != null && project.getClient() != null && project.getClient().getUser() != null
                    && Objects.equals(project.getClient().getUser().getId(), callerUserId)) {
                results.add(slot);
            }
        }
        for (RequirementDocumentApproval slot : approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.DEVELOPER)) {
            ClientProject project = slot.getDocument().getClientProject();
            if (project != null && project.getAssignedDeveloper() != null
                    && Objects.equals(project.getAssignedDeveloper().getId(), callerUserId)) {
                results.add(slot);
            }
        }
        if (userRoleRepository.findRoleCodesByUserId(callerUserId).contains(RoleCode.BUSINESS_ANALYST)) {
            // Every open BA slot, including one on a document this BA authored. The BA slot is the
            // author confirming their own spec; the real cross-checks are the CLIENT and DEVELOPER
            // slots. Mirrors resolveCallerSlot's own self-approval rule below.
            results.addAll(approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.BUSINESS_ANALYST));
        }

        return results.stream()
                // A REJECTED document's still-open slots (whichever parties never got to weigh
                // in before someone else killed it) would otherwise linger in this inbox forever
                // — reject() only flips document.status, it never touches the approval rows
                // themselves. IN_REVIEW-only here; APPROVED is included defensively (a fully
                // approved document has no null-approvedBy slots left to have matched above).
                .filter(slot -> slot.getDocument().getStatus() == RequirementDocumentStatus.IN_REVIEW)
                .map(slot -> {
                    RequirementDocument document = slot.getDocument();
                    ClientProject project = document.getClientProject();
                    return new PendingApprovalResponse(
                            document.getId(),
                            project == null ? null : project.getId(),
                            project == null ? null : project.getTitle(),
                            document.getDocType(),
                            document.getTitle(),
                            document.getVersion(),
                            slot.getApproverRole().name(),
                            document.getAuthoredBy().getFullName());
                })
                .toList();
    }

    /** Decision order (see class Javadoc): the project's own CLIENT contact, then its assigned
     * DEVELOPER, then any BUSINESS_ANALYST — <em>including</em> the document's own author. Product
     * decision: the BA slot is the analyst confirming their own spec, and the meaningful outside
     * sign-off comes from the CLIENT and DEVELOPER slots — requiring a second BA just stalls docs
     * on a small team. Anyone who is none of those three is not a party to this document at all. */
    private RequirementDocumentApproval resolveCallerSlot(RequirementDocument document, ClientProject project,
            List<RequirementDocumentApproval> slots, User caller) {
        if (project != null && project.getClient() != null && project.getClient().getUser() != null
                && Objects.equals(project.getClient().getUser().getId(), caller.getId())) {
            return findSlot(slots, RoleCode.CLIENT)
                    .orElseThrow(() -> new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER));
        }
        if (project != null && project.getAssignedDeveloper() != null
                && Objects.equals(project.getAssignedDeveloper().getId(), caller.getId())) {
            return findSlot(slots, RoleCode.DEVELOPER)
                    .orElseThrow(() -> new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER));
        }
        if (userRoleRepository.findRoleCodesByUserId(caller.getId()).contains(RoleCode.BUSINESS_ANALYST)) {
            return findSlot(slots, RoleCode.BUSINESS_ANALYST)
                    .orElseThrow(() -> new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER));
        }
        throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
    }

    private static Optional<RequirementDocumentApproval> findSlot(List<RequirementDocumentApproval> slots, RoleCode role) {
        return slots.stream().filter(s -> s.getApproverRole() == role).findFirst();
    }

    private RequirementDocument requireDocument(Long id) {
        return requirementDocumentRepository.findWithAssociationsById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REQUIREMENT_DOCUMENT_NOT_FOUND, id));
    }
}
