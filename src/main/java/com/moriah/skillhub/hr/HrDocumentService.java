package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.hr.dto.HrDocumentResponse;
import com.moriah.skillhub.hr.dto.VerifyHrDocumentRequest;
import com.moriah.skillhub.hr.entity.HrDocument;
import com.moriah.skillhub.hr.entity.HrDocumentStatus;
import com.moriah.skillhub.hr.repository.HrDocumentRepository;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * build-plan.md feature 19: "Uploads scanned for content-type and magic-byte mismatch — these
 * are KYC and ID documents." {@link StorageService#upload} already does exactly this (feature
 * 08) — nothing new to build for the scan itself, just call the existing guarded path, same as
 * {@code ResumeService.upload}. Content type is fixed to {@code application/pdf} regardless of
 * what the client's multipart part claims, matching {@code ResumeService}'s own reasoning and
 * architecture.md's literal {@code hr-documents/{userUuid}/{documentType}-{uuid}.pdf} key
 * template — the magic-byte check is the actual security boundary, not the declared content type.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HrDocumentService {

    private static final String HR_DOCUMENT_CONTENT_TYPE = "application/pdf";

    private final HrDocumentRepository hrDocumentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    @Transactional
    public HrDocumentResponse upload(Long userId, String documentType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE, "No file was uploaded.");
        }
        // @RequestParam @NotBlank has no effect without @Validated on the controller — enforced
        // here instead, matching how every other cross-field/manual rule in this feature is
        // enforced at the service layer.
        if (documentType == null || documentType.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "documentType is required.");
        }
        // Audit 2026-08-31 (M9): documentType is a raw request param that flows into the S3 key
        // AND into the hr_documents.document_type column AND back out in the response DTO. Strip
        // everything outside a safe slug alphabet so it can't inject '/' or '..' into the key or
        // markup into the stored/echoed value.
        String safeDocumentType = documentType.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (safeDocumentType.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "documentType must contain letters, digits, '-' or '_'.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));

        String key = "hr-documents/" + user.getUuid() + "/" + safeDocumentType + "-" + UUID.randomUUID() + ".pdf";
        storageService.upload(key, readBytes(file), HR_DOCUMENT_CONTENT_TYPE);

        HrDocument document = new HrDocument();
        document.setUser(user);
        document.setDocumentType(safeDocumentType);
        document.setFileKey(key);
        document.setVerificationStatus(HrDocumentStatus.PENDING);
        hrDocumentRepository.save(document);

        return toResponse(document);
    }

    /** {@code GET /api/v1/hr/documents}. HR_MANAGER/ADMIN see every document (optionally filtered
     * by {@code userUuid} / {@code status} / {@code documentType}); any other authenticated
     * caller is forced to their own — the same "own data unless you're HR" scoping {@link #verify}
     * enforces. {@code downloadUrl} is presigned per row against {@code callerUuid}. */
    @Transactional(readOnly = true)
    public PageResponse<HrDocumentResponse> list(String userUuidFilter, HrDocumentStatus status, String documentType,
                                                 Long callerUserId, String callerUuid, Pageable pageable) {
        boolean isHr = SecurityUtils.currentUserRoles().contains(RoleCode.HR_MANAGER.name())
                || SecurityUtils.currentUserRoles().contains(RoleCode.ADMIN.name());

        Long scopeUserId;
        if (isHr) {
            scopeUserId = userUuidFilter == null || userUuidFilter.isBlank() ? null
                    : userRepository.findByUuid(userUuidFilter)
                            .map(User::getId)
                            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userUuidFilter));
        } else {
            scopeUserId = callerUserId;
        }

        String docType = documentType == null || documentType.isBlank() ? null
                : documentType.trim().replaceAll("[^A-Za-z0-9_-]", "");

        return PageResponse.from(hrDocumentRepository.search(scopeUserId, status, docType, pageable)
                .map(d -> toResponse(d, presign(d.getFileKey(), callerUuid))));
    }

    private String presign(String key, String callerUuid) {
        if (key == null) {
            return null;
        }
        URL url = storageService.presignedGetUrl(callerUuid, key,
                Duration.ofMinutes(Constants.PRESIGNED_URL_TTL_MINUTES));
        return url == null ? null : url.toString();
    }

    @Transactional
    public HrDocumentResponse verify(Long documentId, VerifyHrDocumentRequest request, Long callerUserId) {
        HrDocument document = hrDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.HR_DOCUMENT_NOT_FOUND, documentId));

        if (document.getVerificationStatus() != HrDocumentStatus.PENDING) {
            throw new BusinessException(ErrorCode.HR_DOCUMENT_ALREADY_DECIDED);
        }
        if (callerUserId.equals(document.getUser().getId())) {
            throw new ForbiddenOperationException(ErrorCode.SELF_DECISION_NOT_ALLOWED);
        }

        HrDocumentStatus previousStatus = document.getVerificationStatus();
        document.setVerificationStatus(request.decision());
        document.setVerifiedBy(userRepository.getReferenceById(callerUserId));
        document.setVerifiedAt(Instant.now());
        document.setRejectionReason(request.decision() == HrDocumentStatus.REJECTED ? request.rejectionReason() : null);
        hrDocumentRepository.save(document);
        auditLogService.record(callerUserId, "HR_DOCUMENT_VERIFIED", "HrDocument", document.getId(),
                previousStatus, document.getVerificationStatus());

        return toResponse(document);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("[hr/documents] failed to read uploaded file", e);
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED);
        }
    }

    private HrDocumentResponse toResponse(HrDocument document) {
        return toResponse(document, null);
    }

    private HrDocumentResponse toResponse(HrDocument document, String downloadUrl) {
        return new HrDocumentResponse(
                document.getId(),
                document.getUser().getUuid(),
                document.getUser().getFullName(),
                document.getDocumentType(),
                document.getVerificationStatus(),
                document.getVerifiedBy() == null ? null : document.getVerifiedBy().getUuid(),
                document.getVerifiedAt(),
                document.getRejectionReason(),
                downloadUrl,
                document.getCreatedAt());
    }
}
