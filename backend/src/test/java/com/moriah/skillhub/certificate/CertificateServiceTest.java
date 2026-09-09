package com.moriah.skillhub.certificate;

import com.moriah.skillhub.batch.BatchService;
import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.batch.repository.BatchRepository;
import com.moriah.skillhub.certificate.dto.CertificateResponse;
import com.moriah.skillhub.certificate.dto.IssueCertificateRequest;
import com.moriah.skillhub.certificate.dto.PublicVerificationResponse;
import com.moriah.skillhub.certificate.dto.RevokeCertificateRequest;
import com.moriah.skillhub.certificate.entity.Certificate;
import com.moriah.skillhub.certificate.entity.CertificateType;
import com.moriah.skillhub.certificate.repository.CertificateRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.storage.StorageService;
import com.moriah.skillhub.pip.PipService;
import com.moriah.skillhub.sprint.SprintService;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code CertificateService}'s three eligibility-gate branches (build-plan.md feature 20's own
 * "Verify" line), the two-save numbering scheme, the verification-code collision-retry path, and
 * the revoke idempotency guard. Follows this codebase's {@code PayrollServiceTest}/{@code
 * BatchServiceTest} Mockito conventions — {@code @ExtendWith(MockitoExtension.class)}, a fresh
 * service built per test. {@link QrCodeService} is used <b>for real</b> (no mock, no
 * dependencies of its own) rather than stubbed with a fake byte array — {@code
 * CertificateService#renderCertificatePdf} feeds the QR bytes straight into OpenPDF's real {@code
 * Image.getInstance(byte[])}, which requires an actual decodable image, not an arbitrary stub. */
@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository;
    @Mock
    private BatchRepository batchRepository;
    @Mock
    private BatchService batchService;
    @Mock
    private PipService pipService;
    @Mock
    private SprintService sprintService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private com.moriah.skillhub.sprint.repository.TaskRepository taskRepository;

    private final QrCodeService qrCodeService = new QrCodeService();
    private final CertificateProperties certificateProperties =
            new CertificateProperties("http://localhost:3000/verify/{code}");

    private CertificateService service() {
        return new CertificateService(certificateRepository, batchRepository, batchService, pipService,
                sprintService, userRepository, storageService, qrCodeService, certificateProperties, auditLogService,
                taskRepository);
    }

    private Batch batch(long id) {
        Batch batch = new Batch();
        batch.setId(id);
        batch.setName("Batch " + id);
        batch.setTrackCode("TRK-" + id);
        return batch;
    }

    private User user(long id, String uuid, String fullName) {
        User user = new User();
        user.setId(id);
        user.setUuid(uuid);
        user.setFullName(fullName);
        return user;
    }

    private void stubEligible(Batch batch, User student) {
        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
        when(userRepository.findByUuid(student.getUuid())).thenReturn(Optional.of(student));
        when(batchService.hasGraduatedFromBatch(batch.getId(), student.getId())).thenReturn(true);
        when(pipService.hasOpenPip(student.getId())).thenReturn(false);
        when(sprintService.allSprintsClosed(batch.getId())).thenReturn(true);
    }

    @Test
    void issue_notGraduated_throwsBusinessRuleViolation() {
        Batch batch = batch(1L);
        User student = user(10L, "student-uuid", "Ada Lovelace");
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(student));
        when(batchService.hasGraduatedFromBatch(1L, 10L)).thenReturn(false);

        IssueCertificateRequest request = new IssueCertificateRequest(1L, "student-uuid", null);

        assertThatThrownBy(() -> service().issue(request, 99L, "pm-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(pipService, never()).hasOpenPip(any());
    }

    @Test
    void issue_openPipRecord_throwsBusinessRuleViolation() {
        Batch batch = batch(2L);
        User student = user(11L, "student-uuid-2", "Grace Hopper");
        when(batchRepository.findById(2L)).thenReturn(Optional.of(batch));
        when(userRepository.findByUuid("student-uuid-2")).thenReturn(Optional.of(student));
        when(batchService.hasGraduatedFromBatch(2L, 11L)).thenReturn(true);
        when(pipService.hasOpenPip(11L)).thenReturn(true);

        IssueCertificateRequest request = new IssueCertificateRequest(2L, "student-uuid-2", null);

        assertThatThrownBy(() -> service().issue(request, 99L, "pm-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(sprintService, never()).allSprintsClosed(any());
    }

    @Test
    void issue_sprintsNotAllCompleted_throwsBusinessRuleViolation() {
        Batch batch = batch(3L);
        User student = user(12L, "student-uuid-3", "Katherine Johnson");
        when(batchRepository.findById(3L)).thenReturn(Optional.of(batch));
        when(userRepository.findByUuid("student-uuid-3")).thenReturn(Optional.of(student));
        when(batchService.hasGraduatedFromBatch(3L, 12L)).thenReturn(true);
        when(pipService.hasOpenPip(12L)).thenReturn(false);
        when(sprintService.allSprintsClosed(3L)).thenReturn(false);

        IssueCertificateRequest request = new IssueCertificateRequest(3L, "student-uuid-3", null);

        assertThatThrownBy(() -> service().issue(request, 99L, "pm-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void issue_studentHasUnfinishedTask_throwsBusinessRuleViolation() {
        Batch batch = batch(4L);
        User student = user(13L, "student-uuid-4", "Grace Hopper");
        when(batchRepository.findById(4L)).thenReturn(Optional.of(batch));
        when(userRepository.findByUuid("student-uuid-4")).thenReturn(Optional.of(student));
        when(batchService.hasGraduatedFromBatch(4L, 13L)).thenReturn(true);
        when(pipService.hasOpenPip(13L)).thenReturn(false);
        when(sprintService.allSprintsClosed(4L)).thenReturn(true);
        when(taskRepository.countUnfinishedForStudentInBatch(eq(13L), eq(4L), any())).thenReturn(1L);

        IssueCertificateRequest request = new IssueCertificateRequest(4L, "student-uuid-4", null);

        assertThatThrownBy(() -> service().issue(request, 99L, "pm-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void issue_happyPath_numbersAndUploadsAndReturnsResponse() throws Exception {
        Batch batch = batch(4L);
        User student = user(13L, "student-uuid-4", "Margaret Hamilton");
        stubEligible(batch, student);
        when(userRepository.getReferenceById(99L)).thenReturn(user(99L, "pm-uuid", "PM Name"));
        // save() assigns the IDENTITY id on the first call, same as Hibernate would.
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> {
            Certificate certificate = invocation.getArgument(0);
            if (certificate.getId() == null) {
                certificate.setId(777L);
            }
            return certificate;
        });
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("certificates/MSH-CERT.pdf");
        when(storageService.presignedGetUrl(anyString(), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        IssueCertificateRequest request = new IssueCertificateRequest(4L, "student-uuid-4", CertificateType.EXCELLENCE);

        CertificateResponse response = service().issue(request, 99L, "pm-uuid");

        assertThat(response.certificateNumber()).matches("MSH-CERT-\\d{4}-000777");
        assertThat(response.certificateType()).isEqualTo(CertificateType.EXCELLENCE);
        assertThat(response.userUuid()).isEqualTo("student-uuid-4");
        assertThat(response.verificationCode()).hasSize(12);
        assertThat(response.downloadUrl()).isEqualTo("https://s3.example.com/x");
        verify(certificateRepository, times(2)).save(any(Certificate.class));
        verify(auditLogService).record(eq(99L), eq("CERTIFICATE_ISSUED"), eq("Certificate"), any(), any(), any());
    }

    @Test
    void issue_defaultsCertificateTypeToCompletionWhenOmitted() throws Exception {
        Batch batch = batch(5L);
        User student = user(14L, "student-uuid-5", "Radia Perlman");
        stubEligible(batch, student);
        when(userRepository.getReferenceById(99L)).thenReturn(user(99L, "pm-uuid", "PM Name"));
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> {
            Certificate certificate = invocation.getArgument(0);
            if (certificate.getId() == null) {
                certificate.setId(778L);
            }
            return certificate;
        });
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("certificates/MSH-CERT.pdf");
        when(storageService.presignedGetUrl(anyString(), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        IssueCertificateRequest request = new IssueCertificateRequest(5L, "student-uuid-5", null);

        CertificateResponse response = service().issue(request, 99L, "pm-uuid");

        assertThat(response.certificateType()).isEqualTo(CertificateType.COMPLETION);
    }

    @Test
    void issue_verificationCodeCollidesOnce_retriesAndSucceeds() throws Exception {
        Batch batch = batch(6L);
        User student = user(15L, "student-uuid-6", "Hedy Lamarr");
        stubEligible(batch, student);
        when(userRepository.getReferenceById(99L)).thenReturn(user(99L, "pm-uuid", "PM Name"));
        // First candidate collides, second doesn't.
        when(certificateRepository.existsByVerificationCode(anyString())).thenReturn(true, false);
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> {
            Certificate certificate = invocation.getArgument(0);
            if (certificate.getId() == null) {
                certificate.setId(779L);
            }
            return certificate;
        });
        when(storageService.uploadTrusted(anyString(), any(), anyString())).thenReturn("certificates/MSH-CERT.pdf");
        when(storageService.presignedGetUrl(anyString(), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        IssueCertificateRequest request = new IssueCertificateRequest(6L, "student-uuid-6", null);

        CertificateResponse response = service().issue(request, 99L, "pm-uuid");

        assertThat(response.verificationCode()).hasSize(12);
        verify(certificateRepository, times(2)).existsByVerificationCode(anyString());
    }

    @Test
    void issue_verificationCodeAlwaysCollides_throwsAfterMaxAttempts() {
        Batch batch = batch(7L);
        User student = user(16L, "student-uuid-7", "Annie Easley");
        stubEligible(batch, student);
        when(certificateRepository.existsByVerificationCode(anyString())).thenReturn(true);

        IssueCertificateRequest request = new IssueCertificateRequest(7L, "student-uuid-7", null);

        assertThatThrownBy(() -> service().issue(request, 99L, "pm-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(certificateRepository, times(5)).existsByVerificationCode(anyString());
        verify(certificateRepository, never()).save(any());
    }

    @Test
    void revoke_alreadyRevoked_throwsCertificateAlreadyRevoked() {
        Certificate certificate = new Certificate();
        certificate.setId(1L);
        certificate.setBatch(batch(8L));
        certificate.setUser(user(17L, "student-uuid-8", "Marissa Mayer"));
        certificate.setRevokedAt(Instant.now());
        when(certificateRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(certificate));

        RevokeCertificateRequest request = new RevokeCertificateRequest("Fraud detected.");

        assertThatThrownBy(() -> service().revoke(1L, request, 99L, "pm-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CERTIFICATE_ALREADY_REVOKED);
    }

    @Test
    void revoke_notFound_throwsResourceNotFound() {
        when(certificateRepository.findWithAssociationsById(404L)).thenReturn(Optional.empty());

        RevokeCertificateRequest request = new RevokeCertificateRequest("Fraud detected.");

        assertThatThrownBy(() -> service().revoke(404L, request, 99L, "pm-uuid"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CERTIFICATE_NOT_FOUND);
    }

    @Test
    void revoke_happyPath_setsRevokedAtAndReason() throws Exception {
        Certificate certificate = new Certificate();
        certificate.setId(2L);
        certificate.setBatch(batch(9L));
        certificate.setUser(user(18L, "student-uuid-9", "Mary Jackson"));
        certificate.setPdfKey("certificates/MSH-CERT-2026-000002.pdf");
        when(certificateRepository.findWithAssociationsById(2L)).thenReturn(Optional.of(certificate));
        when(storageService.presignedGetUrl(anyString(), anyString(), any())).thenReturn(new URL("https://s3.example.com/x"));

        RevokeCertificateRequest request = new RevokeCertificateRequest("Fraud detected.");

        CertificateResponse response = service().revoke(2L, request, 99L, "pm-uuid");

        assertThat(response.revokedAt()).isNotNull();
        assertThat(response.revokeReason()).isEqualTo("Fraud detected.");
        verify(auditLogService).record(eq(99L), eq("CERTIFICATE_REVOKED"), eq("Certificate"), any(), any(), any());
    }

    @Test
    void verify_unknownCode_throwsCertificateNotFound() {
        when(certificateRepository.findByVerificationCode("UNKNOWNCODE1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().verify("UNKNOWNCODE1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CERTIFICATE_NOT_FOUND);
    }

    @Test
    void verify_activeCertificate_returnsValidTrue() {
        Certificate certificate = new Certificate();
        certificate.setId(3L);
        certificate.setBatch(batch(10L));
        certificate.setUser(user(19L, "student-uuid-10", "Dorothy Vaughan"));
        certificate.setCertificateType(CertificateType.COMPLETION);
        certificate.setIssuedAt(Instant.now());
        when(certificateRepository.findByVerificationCode("ABCDEFGHJKMN")).thenReturn(Optional.of(certificate));

        PublicVerificationResponse response = service().verify("ABCDEFGHJKMN");

        assertThat(response.valid()).isTrue();
        assertThat(response.revokedAt()).isNull();
        assertThat(response.holderFullName()).isEqualTo("Dorothy Vaughan");
    }

    @Test
    void verify_revokedCertificate_returns200WithValidFalse() {
        Certificate certificate = new Certificate();
        certificate.setId(4L);
        certificate.setBatch(batch(11L));
        certificate.setUser(user(20L, "student-uuid-11", "Joan Clarke"));
        certificate.setCertificateType(CertificateType.COMPLETION);
        certificate.setIssuedAt(Instant.now());
        Instant revokedAt = Instant.now();
        certificate.setRevokedAt(revokedAt);
        when(certificateRepository.findByVerificationCode("REVOKEDCODE1")).thenReturn(Optional.of(certificate));

        PublicVerificationResponse response = service().verify("REVOKEDCODE1");

        assertThat(response.valid()).isFalse();
        assertThat(response.revokedAt()).isEqualTo(revokedAt);
    }
}
