package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.CreateRequirementDocumentRequest;
import com.moriah.skillhub.client.dto.RequirementDocumentDetailResponse;
import com.moriah.skillhub.client.dto.RequirementDocumentResponse;
import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.entity.RequirementDocument;
import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.client.entity.RequirementDocumentType;
import com.moriah.skillhub.client.repository.ClientProjectRepository;
import com.moriah.skillhub.client.repository.RequirementDocumentRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** build-plan.md feature 21: BA document versioning ("a new document for the same
 * (client_project_id, doc_type) pair gets version = max existing version for that pair + 1")
 * and the {@code IN_REVIEW -> APPROVED} approval guard against double-approve. See {@code
 * RequirementDocumentStatus}'s own Javadoc for why {@link RequirementDocumentService#create}
 * skips {@code DRAFT} entirely. */
@ExtendWith(MockitoExtension.class)
class RequirementDocumentServiceTest {

    @Mock
    private RequirementDocumentRepository requirementDocumentRepository;
    @Mock
    private ClientProjectRepository clientProjectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private RequirementDocumentService requirementDocumentService;

    private ClientProject project(long id) {
        ClientProject project = new ClientProject();
        project.setId(id);
        return project;
    }

    private User user(long id, String uuid, String fullName) {
        User user = new User();
        user.setId(id);
        user.setUuid(uuid);
        user.setFullName(fullName);
        return user;
    }

    @Test
    void create_noPriorVersionForPair_startsAtVersion1AndLandsInReview() {
        ClientProject project = project(10L);
        when(clientProjectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "ba-uuid", "Ba One")));
        when(requirementDocumentRepository.findMaxVersion(10L, RequirementDocumentType.BRD)).thenReturn(Optional.empty());
        when(requirementDocumentRepository.save(any(RequirementDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateRequirementDocumentRequest request = new CreateRequirementDocumentRequest(
                10L, RequirementDocumentType.BRD, "Business Requirements", "Scope details.");

        RequirementDocumentResponse response = requirementDocumentService.create(request, 5L);

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(RequirementDocumentStatus.IN_REVIEW);
        assertThat(response.authoredByUuid()).isEqualTo("ba-uuid");
    }

    @Test
    void create_priorVersionExistsForSamePair_incrementsVersion() {
        ClientProject project = project(10L);
        when(clientProjectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "ba-uuid", "Ba One")));
        when(requirementDocumentRepository.findMaxVersion(10L, RequirementDocumentType.BRD)).thenReturn(Optional.of(1));
        when(requirementDocumentRepository.save(any(RequirementDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateRequirementDocumentRequest request = new CreateRequirementDocumentRequest(
                10L, RequirementDocumentType.BRD, "Business Requirements v2", "Revised scope.");

        RequirementDocumentResponse response = requirementDocumentService.create(request, 5L);

        assertThat(response.version()).isEqualTo(2);
    }

    @Test
    void create_differentDocTypeForSameProject_startsItsOwnVersion1() {
        ClientProject project = project(10L);
        when(clientProjectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "ba-uuid", "Ba One")));
        when(requirementDocumentRepository.findMaxVersion(10L, RequirementDocumentType.SRS)).thenReturn(Optional.empty());
        when(requirementDocumentRepository.save(any(RequirementDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateRequirementDocumentRequest request = new CreateRequirementDocumentRequest(
                10L, RequirementDocumentType.SRS, "Software Requirements", "Spec.");

        RequirementDocumentResponse response = requirementDocumentService.create(request, 5L);

        assertThat(response.version()).isEqualTo(1);
    }

    @Test
    void create_clientProjectNotFound_throwsClientProjectNotFound() {
        when(clientProjectRepository.findById(404L)).thenReturn(Optional.empty());

        CreateRequirementDocumentRequest request = new CreateRequirementDocumentRequest(
                404L, RequirementDocumentType.BRD, "T", "C");

        assertThatThrownBy(() -> requirementDocumentService.create(request, 5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLIENT_PROJECT_NOT_FOUND);
    }

    @Test
    void approve_inReview_transitionsToApprovedAndRecordsApprover() {
        RequirementDocument document = new RequirementDocument();
        document.setId(1L);
        document.setVersion(1);
        document.setStatus(RequirementDocumentStatus.IN_REVIEW);
        document.setAuthoredBy(user(5L, "ba-uuid", "Ba One"));
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user(9L, "approver-uuid", "Ba Approver")));

        RequirementDocumentResponse response = requirementDocumentService.approve(1L, 9L);

        assertThat(response.status()).isEqualTo(RequirementDocumentStatus.APPROVED);
        assertThat(response.approvedByUuid()).isEqualTo("approver-uuid");
    }

    @Test
    void approve_alreadyApproved_throwsBusinessRuleViolation() {
        RequirementDocument document = new RequirementDocument();
        document.setId(1L);
        document.setVersion(1);
        document.setStatus(RequirementDocumentStatus.APPROVED);
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> requirementDocumentService.approve(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void approve_notFound_throwsRequirementDocumentNotFound() {
        when(requirementDocumentRepository.findWithAssociationsById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requirementDocumentService.approve(404L, 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REQUIREMENT_DOCUMENT_NOT_FOUND);
    }

    private RequirementDocument doc(long id) {
        RequirementDocument d = new RequirementDocument();
        d.setId(id);
        d.setVersion(1);
        d.setStatus(RequirementDocumentStatus.IN_REVIEW);
        d.setContent("Build a weather dashboard.");
        d.setAuthoredBy(user(5L, "ba-uuid", "Ba One"));
        return d;
    }

    @Test
    void list_passesFiltersThroughAndMapsRows() {
        RequirementDocument d = doc(1L);
        when(requirementDocumentRepository.search(eq(10L), eq(RequirementDocumentStatus.IN_REVIEW), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(d), PageRequest.of(0, 20), 1));

        PageResponse<RequirementDocumentResponse> page = requirementDocumentService.list(
                10L, RequirementDocumentStatus.IN_REVIEW, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).devReviewedAt()).isNull();
    }

    @Test
    void getDetail_includesContent() {
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(doc(1L)));

        RequirementDocumentDetailResponse detail = requirementDocumentService.getDetail(1L);

        assertThat(detail.content()).isEqualTo("Build a weather dashboard.");
    }

    @Test
    void acknowledgeByDeveloper_firstCall_stampsReviewerAndAudits() {
        RequirementDocument d = doc(1L);
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(d));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "dev-uuid", "Dev One")));

        RequirementDocumentDetailResponse detail = requirementDocumentService.acknowledgeByDeveloper(1L, 7L);

        assertThat(d.getDevReviewedAt()).isNotNull();
        assertThat(d.getDevReviewedBy().getUuid()).isEqualTo("dev-uuid");
        assertThat(detail.devReviewedByUuid()).isEqualTo("dev-uuid");
        assertThat(detail.status()).isEqualTo(RequirementDocumentStatus.IN_REVIEW); // unchanged
        verify(auditLogService).record(eq(7L), eq("REQUIREMENT_DOCUMENT_DEV_REVIEWED"), any(), eq(1L), any(), any());
    }

    @Test
    void acknowledgeByDeveloper_secondCall_keepsOriginalReviewerAndDoesNotReaudit() {
        RequirementDocument d = doc(1L);
        d.setDevReviewedAt(java.time.Instant.parse("2026-08-01T00:00:00Z"));
        d.setDevReviewedBy(user(2L, "first-dev", "First Dev"));
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(d));

        RequirementDocumentDetailResponse detail = requirementDocumentService.acknowledgeByDeveloper(1L, 7L);

        assertThat(detail.devReviewedByUuid()).isEqualTo("first-dev");
        assertThat(d.getDevReviewedAt()).isEqualTo(java.time.Instant.parse("2026-08-01T00:00:00Z"));
        verify(userRepository, never()).findById(7L);
        verify(auditLogService, never()).record(any(), eq("REQUIREMENT_DOCUMENT_DEV_REVIEWED"), any(), any(), any(), any());
    }
}
