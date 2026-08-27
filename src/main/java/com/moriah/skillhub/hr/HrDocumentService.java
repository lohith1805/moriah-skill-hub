package com.moriah.skillhub.hr;

import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.hr.dto.HrDocumentResponse;
import com.moriah.skillhub.hr.dto.VerifyHrDocumentRequest;
import com.moriah.skillhub.hr.entity.HrDocument;
import com.moriah.skillhub.hr.entity.HrDocumentStatus;
import com.moriah.skillhub.hr.repository.HrDocumentRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));

        String key = "hr-documents/" + user.getUuid() + "/" + documentType + "-" + UUID.randomUUID() + ".pdf";
        storageService.upload(key, readBytes(file), HR_DOCUMENT_CONTENT_TYPE);

        HrDocument document = new HrDocument();
        document.setUser(user);
        document.setDocumentType(documentType);
        document.setFileKey(key);
        document.setVerificationStatus(HrDocumentStatus.PENDING);
        hrDocumentRepository.save(document);

        return toResponse(document);
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
        return new HrDocumentResponse(
                document.getId(),
                document.getUser().getUuid(),
                document.getDocumentType(),
                document.getVerificationStatus(),
                document.getVerifiedBy() == null ? null : document.getVerifiedBy().getUuid(),
                document.getVerifiedAt(),
                document.getRejectionReason());
    }
}
