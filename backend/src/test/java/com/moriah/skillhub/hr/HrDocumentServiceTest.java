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
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrDocumentServiceTest {

    @Mock
    private HrDocumentRepository hrDocumentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private AuditLogService auditLogService;

    // Built per-test, not as a field initializer — a field initializer runs during construction,
    // before MockitoExtension's beforeEach injects @Mock fields, leaving them null.
    private HrDocumentService service() {
        return new HrDocumentService(hrDocumentRepository, userRepository, storageService, auditLogService);
    }

    private User user(long id, String uuid) {
        User user = new User();
        user.setId(id);
        user.setUuid(uuid);
        return user;
    }

    @Test
    void upload_happyPath_uploadsAndSaves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "user-uuid")));
        when(storageService.upload(anyString(), any(), anyString())).thenReturn("hr-documents/user-uuid/AADHAAR-x.pdf");
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", new byte[]{1, 2, 3});

        HrDocumentResponse response = service().upload(1L, "AADHAAR", file);

        assertThat(response.documentType()).isEqualTo("AADHAAR");
        assertThat(response.verificationStatus()).isEqualTo(HrDocumentStatus.PENDING);
    }

    @Test
    void upload_emptyFile_throwsUnsupportedFileType() {
        MockMultipartFile empty = new MockMultipartFile("file", "id.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service().upload(1L, "AADHAAR", empty))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void upload_blankDocumentType_throwsValidationFailed() {
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service().upload(1L, "  ", file))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    private HrDocument pendingDocument() {
        HrDocument document = new HrDocument();
        document.setId(1L);
        document.setUser(user(1L, "user-uuid"));
        document.setDocumentType("AADHAAR");
        document.setVerificationStatus(HrDocumentStatus.PENDING);
        return document;
    }

    @Test
    void verify_decisionVerified_setsVerifiedByAndStatus() {
        HrDocument document = pendingDocument();
        when(hrDocumentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L, "hr-uuid"));

        HrDocumentResponse response = service().verify(1L,
                new VerifyHrDocumentRequest(HrDocumentStatus.VERIFIED, null), 9L);

        assertThat(response.verificationStatus()).isEqualTo(HrDocumentStatus.VERIFIED);
        assertThat(response.verifiedByUuid()).isEqualTo("hr-uuid");
    }

    @Test
    void verify_decisionRejected_setsRejectionReason() {
        HrDocument document = pendingDocument();
        when(hrDocumentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(userRepository.getReferenceById(9L)).thenReturn(user(9L, "hr-uuid"));

        HrDocumentResponse response = service().verify(1L,
                new VerifyHrDocumentRequest(HrDocumentStatus.REJECTED, "Blurry photo"), 9L);

        assertThat(response.verificationStatus()).isEqualTo(HrDocumentStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Blurry photo");
    }

    @Test
    void verify_callerIsDocumentOwner_throwsForbidden() {
        HrDocument document = pendingDocument();
        when(hrDocumentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service().verify(1L,
                new VerifyHrDocumentRequest(HrDocumentStatus.VERIFIED, null), 1L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SELF_DECISION_NOT_ALLOWED);
    }

    @Test
    void verify_alreadyDecided_throwsAlreadyDecided() {
        HrDocument document = pendingDocument();
        document.setVerificationStatus(HrDocumentStatus.VERIFIED);
        when(hrDocumentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service().verify(1L,
                new VerifyHrDocumentRequest(HrDocumentStatus.REJECTED, "reason"), 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HR_DOCUMENT_ALREADY_DECIDED);
    }

    @Test
    void verify_unknownDocument_throwsNotFound() {
        when(hrDocumentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().verify(99L,
                new VerifyHrDocumentRequest(HrDocumentStatus.VERIFIED, null), 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HR_DOCUMENT_NOT_FOUND);
    }

    // --- list ---

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId, List<String> roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new AuthenticatedPrincipal(userId, "uuid-" + userId, roles), null));
    }

    @Test
    void list_nonHrCaller_scopesToOwnRows_ignoringUserUuidFilter() {
        authenticateAs(3L, List.of("DEVELOPER"));
        var pageable = PageRequest.of(0, 20);
        when(hrDocumentRepository.search(eq(3L), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service().list("uuid-999", null, null, 3L, "uuid-3", pageable);

        verify(hrDocumentRepository).search(eq(3L), isNull(), isNull(), eq(pageable));
    }

    @Test
    void list_hrCallerWithFilters_resolvesUuidAndPassesFilters() {
        authenticateAs(9L, List.of("HR_MANAGER"));
        var pageable = PageRequest.of(0, 20);
        when(userRepository.findByUuid("uuid-1")).thenReturn(Optional.of(user(1L, "uuid-1")));
        when(hrDocumentRepository.search(eq(1L), eq(HrDocumentStatus.PENDING), eq("AADHAAR"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service().list("uuid-1", HrDocumentStatus.PENDING, "AADHAAR", 9L, "uuid-9", pageable);

        verify(hrDocumentRepository).search(eq(1L), eq(HrDocumentStatus.PENDING), eq("AADHAAR"), eq(pageable));
    }
}
