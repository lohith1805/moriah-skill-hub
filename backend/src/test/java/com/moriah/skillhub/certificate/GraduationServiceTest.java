package com.moriah.skillhub.certificate;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.dto.BatchResponse;
import com.moriah.skillhub.batch.dto.GraduationResult;
import com.moriah.skillhub.batch.entity.BatchStatus;
import com.moriah.skillhub.certificate.dto.GraduationResponse;
import com.moriah.skillhub.common.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code GraduationService} is deliberately thin (see its own Javadoc) — this test only proves
 * it wires {@code BatchService#graduate}'s result, {@code BatchService#get}'s batch name, and the
 * audit log write together into the response, not any of the actual graduation business rules
 * (those are {@code BatchServiceTest}'s job). */
@ExtendWith(MockitoExtension.class)
class GraduationServiceTest {

    @Mock
    private BatchService batchService;
    @Mock
    private AuditLogService auditLogService;

    private GraduationService service() {
        return new GraduationService(batchService, auditLogService);
    }

    @Test
    void graduate_composesResponseFromBatchServiceAndAuditsTheOutcome() {
        Instant graduatedAt = Instant.parse("2026-08-27T10:00:00Z");
        when(batchService.graduate(99L, 100L, "student-uuid"))
                .thenReturn(new GraduationResult(5L, "Ada Lovelace", graduatedAt));
        when(batchService.get(100L)).thenReturn(new BatchResponse(
                100L, "Batch A", "TRK-1", "pm-uuid", "PM Name", null,
                LocalDate.now(), LocalDate.now().plusMonths(3), 30, 20, BatchStatus.ACTIVE));

        GraduationResponse response = service().graduate(99L, 100L, "student-uuid");

        assertThat(response.batchId()).isEqualTo(100L);
        assertThat(response.batchName()).isEqualTo("Batch A");
        assertThat(response.userUuid()).isEqualTo("student-uuid");
        assertThat(response.userFullName()).isEqualTo("Ada Lovelace");
        assertThat(response.graduatedAt()).isEqualTo(graduatedAt);
        verify(auditLogService).record(eq(99L), eq("STUDENT_GRADUATED"), eq("User"), eq(5L), any(), any());
    }
}
