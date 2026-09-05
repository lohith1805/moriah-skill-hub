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
 * {@code RequirementDocumentService#approve}, which let any BA (including a document's own
 * author) flip {@code IN_REVIEW -> APPROVED} alone, with no CLIENT or DEVELOPER visibility at
 * all. Now every document gets one required "slot" per {@link #REQUIRED_ROLES}, and the document
 * only reaches {@code APPROVED} once every slot is filled — see {@code
 * RequirementDocumentApproval}'s own Javadoc for the slot model.
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
            // Same exception as resolveCallerSlot's own self-approval block below: a BA's own
            // document is only excluded from their inbox when there's *another* BA to hand it to.
            // Missing this the first time around left a sole BA's own documents fully approvable
            // (via approve()) but permanently invisible in this inbox — confirmed live: a BRD a
            // solo BA authored, with CLIENT already signed off, never appeared here for them to
            // act on, even though nothing else blocked it.
            boolean anotherBaOnStaff = anotherBaOnStaff(callerUserId);
            for (RequirementDocumentApproval slot : approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.BUSINESS_ANALYST)) {
                boolean isOwnDocument = Objects.equals(slot.getDocument().getAuthoredBy().getId(), callerUserId);
                if (!isOwnDocument || !anotherBaOnStaff) {
                    results.add(slot);
                }
            }
        }

        return results.stream()
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
     * DEVELOPER, then any BUSINESS_ANALYST other than the document's own author — self-approval
     * is blocked outright rather than silently falling through to a generic 403, so a BA gets a
     * clear reason. The one exception is a team with a single BA on staff: if no *other* BA
     * exists to hand the slot to, blocking self-approval unconditionally would strand the
     * document (and every doc downstream of it — the developer never gets auto-assigned, since
     * that only fires off a BUSINESS_ANALYST slot being filled) in {@code IN_REVIEW} forever, with
     * no path for anyone to ever fill it. Anyone else is not a party to this document at all. */
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
            if (Objects.equals(document.getAuthoredBy().getId(), caller.getId())) {
                if (anotherBaOnStaff(caller.getId())) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "You authored this document — another business analyst must sign it off.");
                }
                log.info("[requirement-documents] {} is the only business analyst on staff — allowing "
                        + "self sign-off on document {} they authored", caller.getId(), document.getId());
            }
            return findSlot(slots, RoleCode.BUSINESS_ANALYST)
                    .orElseThrow(() -> new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER));
        }
        throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
    }

    /** Shared by {@link #resolveCallerSlot} (may this BA self-approve their own document?) and
     * {@link #pendingFor} (should their own document even show up in their inbox?) — the two
     * questions must have the same answer, or a document becomes approvable but invisible (or
     * visible but rejected on click), either of which is a dead end for the caller. */
    private boolean anotherBaOnStaff(Long callerUserId) {
        return userRoleRepository.findUserIdsByRoleCode(RoleCode.BUSINESS_ANALYST).stream()
                .anyMatch(id -> !Objects.equals(id, callerUserId));
    }

    private static Optional<RequirementDocumentApproval> findSlot(List<RequirementDocumentApproval> slots, RoleCode role) {
        return slots.stream().filter(s -> s.getApproverRole() == role).findFirst();
    }

    private RequirementDocument requireDocument(Long id) {
        return requirementDocumentRepository.findWithAssociationsById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REQUIREMENT_DOCUMENT_NOT_FOUND, id));
    }
}
