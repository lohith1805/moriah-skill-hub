package com.moriah.skillhub.certificate;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.dto.BatchResponse;
import com.moriah.skillhub.batch.dto.GraduationResult;
import com.moriah.skillhub.certificate.dto.GraduationResponse;
import com.moriah.skillhub.common.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * architecture.md's package diagram keeps this deliberately separate from {@link
 * CertificateService} — graduation is a batch-enrollment state change (build-plan.md feature 20:
 * "Nothing else in the system sets this status, and certificate issuance requires it"), not a
 * certificate concern itself; a certificate can only be issued after the fact, never as part of
 * this same call. Thin by design: the actual write (the {@code ACTIVE -> GRADUATED} guard, the
 * seat release) lives in {@link BatchService#graduate}, since that's {@code batch_students}' own
 * table — this class only orchestrates the cross-package call, the audit log, and the response.
 */
@Service
@RequiredArgsConstructor
public class GraduationService {

    private final BatchService batchService;
    private final AuditLogService auditLogService;

    @Transactional
    public GraduationResponse graduate(Long callerUserId, Long batchId, String userUuid) {
        GraduationResult result = batchService.graduate(callerUserId, batchId, userUuid);
        BatchResponse batch = batchService.get(batchId);

        auditLogService.record(callerUserId, "STUDENT_GRADUATED", "User", result.userId(), null, "GRADUATED");

        return new GraduationResponse(batchId, batch.name(), userUuid, result.userFullName(), result.graduatedAt());
    }
}
