package com.moriah.skillhub.hr;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.common.util.Constants;
import com.moriah.skillhub.hr.dto.IssueLetterRequest;
import com.moriah.skillhub.hr.dto.LetterResponse;
import com.moriah.skillhub.hr.entity.Employee;
import com.moriah.skillhub.hr.entity.EmployeeStatus;
import com.moriah.skillhub.hr.entity.LetterType;
import com.moriah.skillhub.hr.repository.EmployeeRepository;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * build-plan.md feature 19: "Letters: offer, internship, experience, relieving. Experience and
 * relieving require GRADUATED or a clean EXITED — never a terminated student." No {@code
 * hr_letters} table exists in architecture.md's V12 schema, unlike every other HR concern this
 * feature covers — a letter is generated, uploaded, and its download URL handed back in one
 * call; nothing about the issuance itself is persisted beyond the S3 object. Key convention
 * ({@code hr-letters/{userUuid}/{type}-{uuid}.pdf}) is this feature's own addition, not in
 * architecture.md's Object Storage list, for the same reason — there's no row to document a
 * template against.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HrLetterService {

    private static final String KEY_PREFIX = "hr-letters/";

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final BatchService batchService;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    /** Not {@code @Transactional} — nothing here writes to the database at all (see this class's
     * own Javadoc: no {@code hr_letters} table), so there is no atomicity to protect. */
    public LetterResponse issue(LetterType type, IssueLetterRequest request, String callerUuid, Long callerUserId) {
        User target = userRepository.findByUuid(request.userUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, request.userUuid()));

        if (type == LetterType.EXPERIENCE || type == LetterType.RELIEVING) {
            requireEligibleForExitLetter(target);
        }

        byte[] pdfBytes = renderLetter(type, target);
        String key = KEY_PREFIX + target.getUuid() + "/" + type + "-" + UUID.randomUUID() + ".pdf";
        storageService.uploadTrusted(key, pdfBytes, "application/pdf");

        Duration ttl = Duration.ofMinutes(Constants.PRESIGNED_URL_TTL_MINUTES);
        URL url = storageService.presignedGetUrl(callerUuid, key, ttl);
        auditLogService.record(callerUserId, "HR_LETTER_ISSUED", "User", target.getId(), null, type);
        log.info("[hr/letters] issued {} letter for user {}", type, target.getUuid());

        return new LetterResponse(url.toString(), Instant.now().plus(ttl));
    }

    /** GRADUATED (student track, via {@link BatchService#hasGraduated}) or a clean {@code
     * EXITED} employee (staff track) — never {@code TERMINATED}, on either track. */
    private void requireEligibleForExitLetter(User target) {
        if (batchService.hasGraduated(target.getId())) {
            return;
        }
        Employee employee = employeeRepository.findByUserId(target.getId()).orElse(null);
        if (employee != null && employee.getStatus() == EmployeeStatus.EXITED) {
            return;
        }
        throw new BusinessException(ErrorCode.LETTER_NOT_ELIGIBLE);
    }

    private byte[] renderLetter(LetterType type, User target) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Moriah Skill Hub"));
            doc.add(new Paragraph(letterTitle(type)));
            doc.add(new Paragraph("Date: " + LocalDate.now()));
            doc.add(new Paragraph("To: " + target.getFullName()));
            doc.add(new Paragraph(letterBody(type, target)));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[hr/letters] {} letter PDF rendering failed for user {}", type, target.getUuid(), e);
            throw new IllegalStateException("Letter PDF rendering failed", e);
        }
    }

    private String letterTitle(LetterType type) {
        return switch (type) {
            case OFFER -> "Offer Letter";
            case INTERNSHIP -> "Internship Letter";
            case EXPERIENCE -> "Experience Letter";
            case RELIEVING -> "Relieving Letter";
        };
    }

    private String letterBody(LetterType type, User target) {
        return switch (type) {
            case OFFER -> "We are pleased to offer " + target.getFullName() + " a position at Moriah Skill Hub.";
            case INTERNSHIP -> "This confirms " + target.getFullName() + "'s internship at Moriah Skill Hub.";
            case EXPERIENCE -> "This certifies that " + target.getFullName() + " has completed their tenure at Moriah Skill Hub.";
            case RELIEVING -> "This confirms that " + target.getFullName() + " has been relieved of their duties at Moriah Skill Hub.";
        };
    }
}
