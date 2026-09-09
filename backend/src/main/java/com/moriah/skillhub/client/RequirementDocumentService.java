package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.CreateRequirementDocumentRequest;
import com.moriah.skillhub.client.dto.RequirementDocumentApprovalResponse;
import com.moriah.skillhub.client.dto.RequirementDocumentDetailResponse;
import com.moriah.skillhub.client.dto.RequirementDocumentResponse;
import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.entity.RequirementDocument;
import com.moriah.skillhub.client.entity.RequirementDocumentApproval;
import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.client.repository.ClientProjectRepository;
import com.moriah.skillhub.client.repository.ClientRepository;
import com.moriah.skillhub.client.repository.RequirementDocumentApprovalRepository;
import com.moriah.skillhub.client.repository.RequirementDocumentRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * build-plan.md feature 21: BA converts client scope into {@code requirement_documents} (BRD/
 * SRS/FRS/user story) with versioning. Approval itself was originally a single BUSINESS_ANALYST/
 * ADMIN action here — it is now multi-party (CLIENT/BUSINESS_ANALYST/DEVELOPER sign-off slots,
 * see {@link RequirementDocumentApprovalService}), so {@link #approve} is a thin delegate kept on
 * this service only so {@code BaController}'s existing {@code PUT /ba/documents/{id}/approve} URL
 * doesn't have to change. See {@code RequirementDocumentStatus}'s own Javadoc for why
 * {@link #create} lands directly in {@code IN_REVIEW}, never {@code DRAFT}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequirementDocumentService {

    private static final List<RoleCode> APPROVAL_ROLE_ORDER =
            List.of(RoleCode.CLIENT, RoleCode.BUSINESS_ANALYST, RoleCode.DEVELOPER);

    private final RequirementDocumentRepository requirementDocumentRepository;
    private final RequirementDocumentApprovalRepository requirementDocumentApprovalRepository;
    private final RequirementDocumentApprovalService requirementDocumentApprovalService;
    private final ClientProjectRepository clientProjectRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /** Versioning: {@code version = max existing version for (clientProjectId, docType) + 1},
     * starting at 1 — older versions are never mutated, pure history (build-plan.md feature 21
     * decision). {@code author} is loaded as a real managed entity (not {@code
     * getReferenceById}) so {@link #toResponse} can read {@code uuid}/{@code fullName} off it
     * with no extra lazy round trip. Pre-creates the document's required approval slots ({@link
     * RequirementDocumentApprovalService#createSlots}) in the same transaction — a document
     * without its slots would be un-approvable by anyone. */
    @Transactional
    public RequirementDocumentResponse create(CreateRequirementDocumentRequest request, Long callerUserId) {
        ClientProject project = requireProject(request.clientProjectId());
        User author = userRepository.findById(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, callerUserId));

        int nextVersion = requirementDocumentRepository.findMaxVersion(project.getId(), request.docType())
                .map(v -> v + 1)
                .orElse(1);

        RequirementDocument document = new RequirementDocument();
        document.setClientProject(project);
        document.setDocType(request.docType());
        document.setTitle(request.title());
        document.setVersion(nextVersion);
        document.setContent(request.content());
        document.setStatus(RequirementDocumentStatus.IN_REVIEW);
        document.setAuthoredBy(author);
        requirementDocumentRepository.save(document);
        List<RequirementDocumentApproval> slots = requirementDocumentApprovalService.createSlots(document);

        log.info("[ba/documents] created {} v{} for client project {}", request.docType(), nextVersion, project.getId());
        return toResponse(document, slots);
    }

    /** {@code PUT /api/v1/ba/documents/{id}/approve} and {@code POST /api/v1/requirement-documents
     * /{id}/approve} — same underlying action, kept as two URLs so the BA portal's existing route
     * doesn't change while CLIENT/DEVELOPER get their own entry point. All the actual role
     * inference, self-approval blocking, and developer auto-assignment lives in {@link
     * RequirementDocumentApprovalService#approve}. */
    @Transactional
    public RequirementDocumentResponse approve(Long documentId, Long callerUserId) {
        RequirementDocument document = requirementDocumentApprovalService.approve(documentId, callerUserId);
        return toResponse(document);
    }

    /** {@code PUT /api/v1/ba/documents/{id}/reject} and {@code POST /api/v1/requirement-documents
     * /{id}/reject} — same two-URL shape as {@link #approve}, same reason. All the actual role
     * inference and status transition lives in {@link RequirementDocumentApprovalService#reject}. */
    @Transactional
    public RequirementDocumentResponse reject(Long documentId, Long callerUserId, String reason) {
        RequirementDocument document = requirementDocumentApprovalService.reject(documentId, callerUserId, reason);
        return toResponse(document);
    }

    /** {@code GET /api/v1/ba/documents} and {@code GET /api/v1/dev/requirement-documents} — same
     * rows, the caller's role decides which route they came in on. */
    @Transactional(readOnly = true)
    public PageResponse<RequirementDocumentResponse> list(Long clientProjectId,
            RequirementDocumentStatus status, Pageable pageable) {
        Page<RequirementDocument> page = requirementDocumentRepository.search(clientProjectId, status, pageable);
        Map<Long, List<RequirementDocumentApproval>> approvalsByDocId = batchApprovals(page.getContent());
        return PageResponse.from(
                page.map(d -> toResponse(d, approvalsByDocId.getOrDefault(d.getId(), List.of()))));
    }

    /** {@code GET /api/v1/requirement-documents} — the CLIENT/DEVELOPER-reachable equivalent of
     * {@link #list}: a CLIENT may only ever see their own project's documents (staff — BA/ADMIN —
     * and DEVELOPER keep the same unrestricted read {@code /ba/documents}/{@code
     * /dev/requirement-documents} already have; nothing about visibility changes for them here,
     * only CLIENT gains access at all). */
    @Transactional(readOnly = true)
    public PageResponse<RequirementDocumentResponse> listForCaller(Long clientProjectId,
            RequirementDocumentStatus status, Long callerUserId, Pageable pageable) {
        if (isClientOnly()) {
            ClientProject project = clientProjectRepository.findById(clientProjectId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLIENT_PROJECT_NOT_FOUND, clientProjectId));
            requireOwningClient(project, callerUserId);
        }
        return list(clientProjectId, status, pageable);
    }

    @Transactional(readOnly = true)
    public RequirementDocumentDetailResponse getDetail(Long documentId) {
        return toDetailResponse(requireDocument(documentId));
    }

    /** {@code GET /api/v1/requirement-documents/{id}} — same document {@link #getDetail} serves
     * BA/ADMIN, but reachable by CLIENT/DEVELOPER too; a CLIENT-only caller gets a 403 unless the
     * document's project is their own (staff and DEVELOPER keep the existing unrestricted read —
     * see {@link #listForCaller}'s own note on why). */
    @Transactional(readOnly = true)
    public RequirementDocumentDetailResponse getDetailForCaller(Long documentId, Long callerUserId) {
        RequirementDocument document = requireDocument(documentId);
        if (isClientOnly()) {
            ClientProject project = document.getClientProject();
            if (project == null) {
                throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
            }
            requireOwningClient(project, callerUserId);
        }
        return toDetailResponse(document);
    }

    /**
     * {@code POST /api/v1/dev/requirement-documents/{id}/acknowledge} (gap B1.16) — a developer
     * records that they have read the requirement. Idempotent: the first acknowledgement's
     * timestamp and reviewer are kept; a second call is a no-op that still returns the current
     * state. Does not touch {@code status} (the BA approval axis).
     */
    @Transactional
    public RequirementDocumentDetailResponse acknowledgeByDeveloper(Long documentId, Long callerUserId) {
        RequirementDocument document = requireDocument(documentId);
        if (document.getDevReviewedAt() == null) {
            User reviewer = userRepository.findById(callerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, callerUserId));
            document.setDevReviewedAt(Instant.now());
            document.setDevReviewedBy(reviewer);
            auditLogService.record(callerUserId, "REQUIREMENT_DOCUMENT_DEV_REVIEWED", "RequirementDocument",
                    document.getId(), null, document.getVersion());
            log.info("[dev/requirement-documents] {} acknowledged document {} v{}",
                    callerUserId, document.getId(), document.getVersion());
        }
        return toDetailResponse(document);
    }

    private ClientProject requireProject(Long id) {
        return clientProjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLIENT_PROJECT_NOT_FOUND, id));
    }

    private RequirementDocument requireDocument(Long id) {
        return requirementDocumentRepository.findWithAssociationsById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REQUIREMENT_DOCUMENT_NOT_FOUND, id));
    }

    /** {@code true} only for a caller holding CLIENT and none of BUSINESS_ANALYST/DEVELOPER/ADMIN
     * — staff and developers keep the unrestricted read they already had before CLIENT gained
     * access at all (see {@link #listForCaller}/{@link #getDetailForCaller}). */
    private boolean isClientOnly() {
        List<String> roles = SecurityUtils.currentUserRoles();
        return roles.contains(RoleCode.CLIENT.name())
                && !roles.contains(RoleCode.ADMIN.name())
                && !roles.contains(RoleCode.BUSINESS_ANALYST.name())
                && !roles.contains(RoleCode.DEVELOPER.name());
    }

    private void requireOwningClient(ClientProject project, Long callerUserId) {
        User owner = project.getClient() == null ? null : project.getClient().getUser();
        if (owner == null || !Objects.equals(owner.getId(), callerUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }

    /** {@code RequirementDocumentApprovalRepository#findByDocumentIdIn}'s batched per-page lookup
     * — one flat query for a whole page of documents instead of one {@code findByDocumentId} call
     * per row (code-standards.md "N+1 Prevention"). */
    private Map<Long, List<RequirementDocumentApproval>> batchApprovals(List<RequirementDocument> documents) {
        if (documents.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = documents.stream().map(RequirementDocument::getId).toList();
        return requirementDocumentApprovalRepository.findByDocumentIdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getDocument().getId()));
    }

    private static List<RequirementDocumentApprovalResponse> toApprovalResponses(List<RequirementDocumentApproval> approvals) {
        return approvals.stream()
                .sorted(Comparator.comparingInt(a -> APPROVAL_ROLE_ORDER.indexOf(a.getApproverRole())))
                .map(a -> new RequirementDocumentApprovalResponse(
                        a.getApproverRole().name(),
                        a.getApprovedBy() == null ? null : a.getApprovedBy().getUuid(),
                        a.getApprovedBy() == null ? null : a.getApprovedBy().getFullName(),
                        a.getApprovedAt()))
                .toList();
    }

    private RequirementDocumentResponse toResponse(RequirementDocument document) {
        return toResponse(document, requirementDocumentApprovalRepository.findByDocumentId(document.getId()));
    }

    private RequirementDocumentResponse toResponse(RequirementDocument document, List<RequirementDocumentApproval> approvals) {
        User author = document.getAuthoredBy();
        User approver = document.getApprovedBy();
        User devReviewer = document.getDevReviewedBy();
        User rejecter = document.getRejectedBy();
        ClientProject project = document.getClientProject();
        return new RequirementDocumentResponse(
                document.getId(),
                project == null ? null : project.getId(),
                project == null || project.getClient() == null ? null : project.getClient().getCompanyName(),
                project == null ? null : project.getTitle(),
                document.getDocType(),
                document.getTitle(),
                document.getVersion(),
                document.getStatus(),
                author.getUuid(),
                author.getFullName(),
                approver == null ? null : approver.getUuid(),
                approver == null ? null : approver.getFullName(),
                devReviewer == null ? null : devReviewer.getUuid(),
                devReviewer == null ? null : devReviewer.getFullName(),
                document.getDevReviewedAt(),
                rejecter == null ? null : rejecter.getUuid(),
                rejecter == null ? null : rejecter.getFullName(),
                document.getRejectedAt(),
                document.getRejectionReason(),
                toApprovalResponses(approvals));
    }

    private RequirementDocumentDetailResponse toDetailResponse(RequirementDocument document) {
        User author = document.getAuthoredBy();
        User approver = document.getApprovedBy();
        User devReviewer = document.getDevReviewedBy();
        User rejecter = document.getRejectedBy();
        List<RequirementDocumentApproval> approvals = requirementDocumentApprovalRepository.findByDocumentId(document.getId());
        ClientProject project = document.getClientProject();
        return new RequirementDocumentDetailResponse(
                document.getId(),
                project == null ? null : project.getId(),
                project == null || project.getClient() == null ? null : project.getClient().getCompanyName(),
                project == null ? null : project.getTitle(),
                document.getDocType(),
                document.getTitle(),
                document.getVersion(),
                document.getStatus(),
                document.getContent(),
                author.getUuid(),
                author.getFullName(),
                approver == null ? null : approver.getUuid(),
                approver == null ? null : approver.getFullName(),
                devReviewer == null ? null : devReviewer.getUuid(),
                devReviewer == null ? null : devReviewer.getFullName(),
                document.getDevReviewedAt(),
                rejecter == null ? null : rejecter.getUuid(),
                rejecter == null ? null : rejecter.getFullName(),
                document.getRejectedAt(),
                document.getRejectionReason(),
                toApprovalResponses(approvals));
    }
}
