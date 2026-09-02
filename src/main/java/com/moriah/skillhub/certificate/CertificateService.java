package com.moriah.skillhub.certificate;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.certificate.dto.CertificateResponse;
import com.moriah.skillhub.certificate.dto.CertificateSummaryProjection;
import com.moriah.skillhub.certificate.dto.IssueCertificateRequest;
import com.moriah.skillhub.certificate.dto.PublicVerificationResponse;
import com.moriah.skillhub.certificate.dto.RevokeCertificateRequest;
import com.moriah.skillhub.certificate.entity.Certificate;
import com.moriah.skillhub.certificate.entity.CertificateType;
import com.moriah.skillhub.certificate.repository.CertificateRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.pip.PipService;
import com.moriah.skillhub.sprint.SprintService;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.List;

/**
 * build-plan.md feature 20. Issuance's three eligibility checks each call into a sibling module's
 * *service* — never its repository or entity (architecture.md layer rule) — exactly the boundary
 * {@code BatchService#hasGraduatedFromBatch}/{@code PipService#hasOpenPip}/{@code
 * SprintService#allSprintsClosed} each document on their own side. {@code BatchRepository} is
 * still injected directly here, same as {@code SprintService} injects it — {@code Batch} is the
 * established shared-kernel entity, not a boundary violation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateService {

    /** Excludes {@code 0}/{@code O} and {@code 1}/{@code I}/{@code L} — build-plan.md feature 20:
     * a verification code may need to be typed by hand off a printed certificate, and those five
     * characters are the classic manual-transcription ambiguity set. 31 characters remain (8
     * digits + 23 letters). */
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 12;
    private static final String KEY_PREFIX = "certificates/";

    private final SecureRandom secureRandom = new SecureRandom();

    private final CertificateRepository certificateRepository;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final PipService pipService;
    private final SprintService sprintService;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final QrCodeService qrCodeService;
    private final CertificateProperties certificateProperties;
    private final AuditLogService auditLogService;

    /** The three eligibility checks build-plan.md's "Verify" line names explicitly: "A student
     * with an open PIP cannot be issued. A student never graduated cannot be issued." (the third,
     * "all sprints closed," has no dedicated verify-line student-facing symptom but is spec'd in
     * the same "Logic" bullet). Each failure is a plain {@code BUSINESS_RULE_VIOLATION} with a
     * specific message, not a bespoke {@code ErrorCode} per branch — matching the established
     * precedent for one-off state guards in this codebase ({@code BatchService#updatePipStatus}).
     * <p>
     * Numbering and code generation: the row is saved once with no {@code certificateNumber} set
     * (assigning its {@code IDENTITY} id), then {@code certificateNumber} is computed from that id
     * and saved again — two writes, deliberately, since the number can't be known before the id
     * exists (Constants#CERTIFICATE_PREFIX's own Javadoc). Low-frequency admin action, not a hot
     * path, so the extra round trip is a non-issue.
     * <p>
     * Feature 23 hardening: deliberately <b>not</b> {@code @Transactional} any more — {@code
     * qrCodeService.png}/{@code storageService.uploadTrusted} below are outbound calls (S3, and
     * QR rendering that itself calls out), and AGENTS.md is explicit ("never make an outbound
     * HTTP call inside a transaction"). This was a genuine, pre-existing violation (a `/review`
     * finding deferred from feature 20, tracked in progress-tracker.md, resolved here alongside
     * its three siblings — {@code InvoiceService.renderAndUpload}, {@code
     * PayrollService.generate}, and {@code HrLetterService.issue}, the last of which turned out to
     * already be correct — see its own Javadoc). Each repository call below now runs in its own
     * short transaction via Spring Data's per-call proxy default, same precedent {@code
     * SubmissionVerificationRetryJob.retry()}'s own Javadoc documents for this exact "reads/writes,
     * then an external call, then a final write" shape. The one accepted, narrow trade-off: the
     * two-step numbering ({@code save} with no number, then {@code save} again with the computed
     * one) is no longer atomic against a mid-request crash — a process death in the few CPU
     * instructions between those two calls (no I/O in between) would leave a certificate row with
     * a permanently {@code NULL certificate_number}, recoverable by an ops backfill, never a
     * security or financial-correctness issue. Judged an acceptable cost for a purely
     * theoretical, no-I/O crash window, not worth a dedicated companion "Writer" bean (the pattern
     * {@code QuizAttemptWriter}/{@code TaskSubmissionWriter} established elsewhere in this
     * codebase for a genuinely concurrent race, which this is not) for this hardening pass's
     * budget.
     */
    public CertificateResponse issue(IssueCertificateRequest request, Long callerUserId, String callerUuid) {
        Batch batch = requireBatch(request.batchId());
        batchService.requireOwnerOrAdmin(callerUserId, batch);
        User student = requireUserByUuid(request.userUuid());

        if (!batchService.hasGraduatedFromBatch(batch.getId(), student.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This student has not graduated from this batch yet.");
        }
        if (pipService.hasOpenPip(student.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This student has an open PIP record and cannot be issued a certificate.");
        }
        if (!sprintService.allSprintsClosed(batch.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Not every sprint in this batch is COMPLETED yet.");
        }

        CertificateType type = request.certificateType() != null ? request.certificateType() : CertificateType.COMPLETION;
        // Audit 2026-08-31 (M17): calling issue() twice would otherwise mint two certificate rows,
        // two PDFs and two verification codes for the same graduation. A previously-revoked one
        // does not block a corrected re-issue.
        if (certificateRepository.existsByUserIdAndBatchIdAndCertificateTypeAndRevokedAtIsNull(
                student.getId(), batch.getId(), type)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "A " + type + " certificate has already been issued to this student for this batch.");
        }

        Certificate certificate = new Certificate();
        certificate.setUser(student);
        certificate.setBatch(batch);
        certificate.setCertificateType(type);
        certificate.setVerificationCode(generateUniqueVerificationCode());
        certificate.setIssuedBy(userRepository.getReferenceById(callerUserId));
        certificate.setIssuedAt(Instant.now());
        certificateRepository.save(certificate);

        certificate.setCertificateNumber(buildCertificateNumber(certificate.getId()));
        certificateRepository.save(certificate);

        byte[] qrPng = qrCodeService.png(certificateProperties.verifyUrl(certificate.getVerificationCode()), 150);
        byte[] pdfBytes = renderCertificatePdf(certificate, student, batch, qrPng);
        String key = KEY_PREFIX + certificate.getCertificateNumber() + ".pdf";
        certificate.setPdfKey(storageService.uploadTrusted(key, pdfBytes, "application/pdf"));

        auditLogService.record(callerUserId, "CERTIFICATE_ISSUED", "Certificate", certificate.getId(),
                null, certificate.getCertificateNumber());
        log.info("[certificate/issue] issued {} for user {} batch {}",
                certificate.getCertificateNumber(), student.getUuid(), batch.getId());

        return toResponse(certificate, student, callerUuid);
    }

    /** {@code GET /api/v1/certificates/me} — the caller's own certificates, paginated (AGENTS.md:
     * "every list endpoint is paginated"). Loads the caller's {@code User} once for the whole page
     * rather than letting {@code toResponse} touch {@code certificate.getUser()} per row — {@code
     * findByUserId}'s own {@code @EntityGraph} only eager-loads {@code batch}, so reading {@code
     * user} off each row here would be an N+1 for information the caller already supplied. */
    @Transactional(readOnly = true)
    public PageResponse<CertificateResponse> me(Long callerUserId, String callerUuid, Pageable pageable) {
        User caller = userRepository.findById(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, callerUserId));
        return PageResponse.from(certificateRepository.findByUserId(callerUserId, pageable)
                .map(certificate -> toResponse(certificate, caller, callerUuid)));
    }

    @Transactional
    public CertificateResponse revoke(Long certificateId, RevokeCertificateRequest request, Long callerUserId, String callerUuid) {
        Certificate certificate = certificateRepository.findWithAssociationsById(certificateId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CERTIFICATE_NOT_FOUND, certificateId));
        batchService.requireOwnerOrAdmin(callerUserId, certificate.getBatch());
        if (certificate.getRevokedAt() != null) {
            throw new BusinessException(ErrorCode.CERTIFICATE_ALREADY_REVOKED);
        }

        certificate.setRevokedAt(Instant.now());
        certificate.setRevokeReason(request.reason());

        auditLogService.record(callerUserId, "CERTIFICATE_REVOKED", "Certificate", certificate.getId(),
                null, request.reason());
        log.info("[certificate/revoke] revoked {}", certificate.getCertificateNumber());

        return toResponse(certificate, certificate.getUser(), callerUuid);
    }

    /** {@code GET /api/v1/certificates/verify/{code}} — public, unauthenticated, no {@code
     * @PreAuthorize} (wired that way in {@code VerificationController}). Looks up by {@code
     * verification_code} only, never a certificate id (build-plan.md is explicit about this — see
     * {@code CertificateRepository#findByVerificationCode}'s own Javadoc). An unknown code is a
     * genuine 404 (it never existed); a real-but-revoked certificate is a completely different
     * case that must still return {@code 200} with {@code valid: false} — never conflate the two
     * into "not found." */
    @Transactional(readOnly = true)
    public PublicVerificationResponse verify(String code) {
        Certificate certificate = certificateRepository.findByVerificationCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CERTIFICATE_NOT_FOUND, code));

        return new PublicVerificationResponse(
                certificate.getUser().getFullName(),
                certificate.getBatch().getName(),
                certificate.getBatch().getTrackCode(),
                certificate.getCertificateType(),
                certificate.getIssuedAt(),
                certificate.getRevokedAt() == null,
                certificate.getRevokedAt());
    }

    /** {@code UserService#getPortfolio}'s {@code issuedCertificates} field — non-revoked only, a
     * revoked certificate is never shown as a portfolio achievement. Deliberately unpaginated —
     * see {@code CertificateRepository#findByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc}'s own
     * Javadoc for why this is the one AGENTS.md "every list is paginated" exception this project
     * already established. */
    @Transactional(readOnly = true)
    public List<CertificateSummaryProjection> issuedCertificatesFor(Long userId) {
        return certificateRepository.findByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(userId).stream()
                .map(c -> new CertificateSummaryProjection(c.getCertificateType(), c.getIssuedAt(), c.getVerificationCode()))
                .toList();
    }

    private String generateUniqueVerificationCode() {
        for (int attempt = 1; attempt <= Constants.MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!certificateRepository.existsByVerificationCode(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "Could not generate a unique verification code.");
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private String buildCertificateNumber(Long certificateId) {
        return "%s-%d-%06d".formatted(Constants.CERTIFICATE_PREFIX, Year.now().getValue(), certificateId);
    }

    private byte[] renderCertificatePdf(Certificate certificate, User student, Batch batch, byte[] qrPng) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Moriah Skill Hub"));
            doc.add(new Paragraph("Certificate of "
                    + (certificate.getCertificateType() == CertificateType.EXCELLENCE ? "Excellence" : "Completion")));
            doc.add(new Paragraph("This certifies that " + student.getFullName()
                    + " has successfully completed " + batch.getName() + " (" + batch.getTrackCode() + ")."));
            doc.add(new Paragraph("Certificate No: " + certificate.getCertificateNumber()));
            doc.add(new Paragraph("Verification Code: " + certificate.getVerificationCode()));
            doc.add(new Paragraph("Issued: " + certificate.getIssuedAt()));

            Image qrImage = Image.getInstance(qrPng);
            qrImage.scaleAbsolute(120f, 120f);
            doc.add(qrImage);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[certificate/pdf] rendering failed for certificate {}", certificate.getCertificateNumber(), e);
            throw new IllegalStateException("Certificate PDF rendering failed", e);
        }
    }

    private CertificateResponse toResponse(Certificate certificate, User user, String presignCallerUuid) {
        String downloadUrl = certificate.getPdfKey() == null ? null : presign(certificate.getPdfKey(), presignCallerUuid);
        return new CertificateResponse(
                certificate.getId(),
                certificate.getCertificateNumber(),
                user.getUuid(),
                user.getFullName(),
                certificate.getBatch().getId(),
                certificate.getBatch().getName(),
                certificate.getCertificateType(),
                certificate.getVerificationCode(),
                downloadUrl,
                certificate.getIssuedAt(),
                certificate.getRevokedAt(),
                certificate.getRevokeReason());
    }

    /** {@code OwnershipGuard} recognizes {@code certificates/{certificateNumber}.pdf} — the
     * certificate's own user, or a {@code TRAINER_PM}/{@code ADMIN}, may sign it (see {@code
     * OwnershipGuard#canAccessCertificate}). */
    private String presign(String key, String callerUuid) {
        URL url = storageService.presignedGetUrl(callerUuid, key, Duration.ofMinutes(Constants.PRESIGNED_URL_TTL_MINUTES));
        return url.toString();
    }

    private Batch requireBatch(Long batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BATCH_NOT_FOUND, batchId));
    }

    private User requireUserByUuid(String uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, uuid));
    }
}
