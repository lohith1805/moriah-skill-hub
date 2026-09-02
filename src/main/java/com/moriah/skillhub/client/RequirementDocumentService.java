package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.CreateRequirementDocumentRequest;
import com.moriah.skillhub.client.dto.RequirementDocumentDetailResponse;
import com.moriah.skillhub.client.dto.RequirementDocumentResponse;
import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.entity.RequirementDocument;
import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.client.repository.ClientProjectRepository;
import com.moriah.skillhub.client.repository.RequirementDocumentRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * build-plan.md feature 21: BA converts client scope into {@code requirement_documents} (BRD/
 * SRS/FRS/user story) with versioning; approval restricted to BUSINESS_ANALYST/ADMIN. See {@code
 * RequirementDocumentStatus}'s own Javadoc for why {@link #create} lands directly in {@code
 * IN_REVIEW}, never {@code DRAFT}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequirementDocumentService {

    private final RequirementDocumentRepository requirementDocumentRepository;
    private final ClientProjectRepository clientProjectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /** Versioning: {@code version = max existing version for (clientProjectId, docType) + 1},
     * starting at 1 — older versions are never mutated, pure history (build-plan.md feature 21
     * decision). {@code author} is loaded as a real managed entity (not {@code
     * getReferenceById}) so {@link #toResponse} can read {@code uuid}/{@code fullName} off it
     * with no extra lazy round trip. */
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

        log.info("[ba/documents] created {} v{} for client project {}", request.docType(), nextVersion, project.getId());
        return toResponse(document);
    }

    /** Guards against double-approve — {@code BUSINESS_RULE_VIOLATION}, matching {@code
     * CertificateService#revoke}'s/{@code PayrollService}'s established one-off-state-guard
     * precedent, not a bespoke {@code ErrorCode}. */
    @Transactional
    public RequirementDocumentResponse approve(Long documentId, Long callerUserId) {
        RequirementDocument document = requireDocument(documentId);

        if (document.getStatus() == RequirementDocumentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "This document has already been approved.");
        }

        User approver = userRepository.findById(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, callerUserId));
        document.setStatus(RequirementDocumentStatus.APPROVED);
        document.setApprovedBy(approver);

        auditLogService.record(callerUserId, "REQUIREMENT_DOCUMENT_APPROVED", "RequirementDocument",
                document.getId(), null, document.getVersion());
        log.info("[ba/documents] approved document {} v{}", document.getId(), document.getVersion());

        return toResponse(document);
    }

    /** {@code GET /api/v1/ba/documents} and {@code GET /api/v1/dev/requirement-documents} — same
     * rows, the caller's role decides which route they came in on. */
    @Transactional(readOnly = true)
    public PageResponse<RequirementDocumentResponse> list(Long clientProjectId,
            RequirementDocumentStatus status, Pageable pageable) {
        return PageResponse.from(
                requirementDocumentRepository.search(clientProjectId, status, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public RequirementDocumentDetailResponse getDetail(Long documentId) {
        return toDetailResponse(requireDocument(documentId));
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

    private RequirementDocumentResponse toResponse(RequirementDocument document) {
        User author = document.getAuthoredBy();
        User approver = document.getApprovedBy();
        User devReviewer = document.getDevReviewedBy();
        return new RequirementDocumentResponse(
                document.getId(),
                document.getClientProject() == null ? null : document.getClientProject().getId(),
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
                document.getDevReviewedAt());
    }

    private RequirementDocumentDetailResponse toDetailResponse(RequirementDocument document) {
        User author = document.getAuthoredBy();
        User approver = document.getApprovedBy();
        User devReviewer = document.getDevReviewedBy();
        return new RequirementDocumentDetailResponse(
                document.getId(),
                document.getClientProject() == null ? null : document.getClientProject().getId(),
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
                document.getDevReviewedAt());
    }
}
