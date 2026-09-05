package com.moriah.skillhub.client;

import com.moriah.skillhub.client.entity.Client;
import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.entity.RequirementDocument;
import com.moriah.skillhub.client.entity.RequirementDocumentApproval;
import com.moriah.skillhub.client.entity.RequirementDocumentStatus;
import com.moriah.skillhub.client.entity.RequirementDocumentType;
import com.moriah.skillhub.client.dto.PendingApprovalResponse;
import com.moriah.skillhub.client.repository.ClientProjectRepository;
import com.moriah.skillhub.client.repository.RequirementDocumentApprovalRepository;
import com.moriah.skillhub.client.repository.RequirementDocumentRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRepository;
import com.moriah.skillhub.user.repository.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The user's own critique of the old single-approver flow ("this approve button ... is not
 * necessary for all docs" / "if there are three developers which developer will get the client
 * docs ... need [to] add a round robin") drives every branch here: role-inferred slot resolution,
 * self-approval blocking, the ADMIN fast-track, and the once-only developer auto-assignment on a
 * BRD/FRS's first BUSINESS_ANALYST sign-off. Reads {@code SecurityUtils.currentUserRoles()} off
 * the real {@code SecurityContextHolder} — same pattern {@code ClientProjectServiceTest} already
 * established. */
@ExtendWith(MockitoExtension.class)
class RequirementDocumentApprovalServiceTest {

    @Mock
    private RequirementDocumentApprovalRepository approvalRepository;
    @Mock
    private RequirementDocumentRepository requirementDocumentRepository;
    @Mock
    private ClientProjectRepository clientProjectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private StaffAssignmentService staffAssignmentService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private RequirementDocumentApprovalService approvalService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId, RoleCode... roles) {
        List<String> roleNames = List.of(roles).stream().map(Enum::name).toList();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, "uuid-" + userId, roleNames);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }

    private User user(long id, String uuid) {
        User user = new User();
        user.setId(id);
        user.setUuid(uuid);
        user.setFullName("User " + id);
        return user;
    }

    private ClientProject project(long id, User clientUser, User assignedDeveloper) {
        ClientProject project = new ClientProject();
        project.setId(id);
        project.setTitle("Storefront Revamp");
        if (clientUser != null) {
            Client client = new Client();
            client.setId(id);
            client.setUser(clientUser);
            project.setClient(client);
        }
        project.setAssignedDeveloper(assignedDeveloper);
        return project;
    }

    private RequirementDocument document(long id, RequirementDocumentType docType, ClientProject project, User author) {
        RequirementDocument document = new RequirementDocument();
        document.setId(id);
        document.setVersion(1);
        document.setDocType(docType);
        document.setStatus(RequirementDocumentStatus.IN_REVIEW);
        document.setClientProject(project);
        document.setAuthoredBy(author);
        return document;
    }

    // ---- createSlots ----

    @Test
    void createSlots_brd_createsClientBaAndDeveloperSlots() {
        RequirementDocument document = document(1L, RequirementDocumentType.BRD, project(10L, user(1L, "c"), null), user(5L, "ba"));
        when(approvalRepository.save(any(RequirementDocumentApproval.class))).thenAnswer(inv -> inv.getArgument(0));

        List<RequirementDocumentApproval> slots = approvalService.createSlots(document);

        assertThat(slots).extracting(RequirementDocumentApproval::getApproverRole)
                .containsExactly(RoleCode.CLIENT, RoleCode.BUSINESS_ANALYST, RoleCode.DEVELOPER);
        assertThat(slots).allMatch(s -> s.getApprovedBy() == null);
    }

    @Test
    void createSlots_srs_createsOnlyBaAndDeveloperSlots_noClientSlot() {
        RequirementDocument document = document(1L, RequirementDocumentType.SRS, project(10L, user(1L, "c"), null), user(5L, "ba"));
        when(approvalRepository.save(any(RequirementDocumentApproval.class))).thenAnswer(inv -> inv.getArgument(0));

        List<RequirementDocumentApproval> slots = approvalService.createSlots(document);

        assertThat(slots).extracting(RequirementDocumentApproval::getApproverRole)
                .containsExactly(RoleCode.BUSINESS_ANALYST, RoleCode.DEVELOPER);
    }

    // ---- approve: not found / already approved ----

    @Test
    void approve_documentNotFound_throwsRequirementDocumentNotFound() {
        when(requirementDocumentRepository.findWithAssociationsById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.approve(404L, 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REQUIREMENT_DOCUMENT_NOT_FOUND);
    }

    @Test
    void approve_documentAlreadyFullyApproved_throwsBusinessRuleViolation() {
        RequirementDocument document = document(1L, RequirementDocumentType.SRS, project(10L, null, null), user(5L, "ba"));
        document.setStatus(RequirementDocumentStatus.APPROVED);
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> approvalService.approve(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    // ---- approve: role resolution ----

    @Test
    void approve_businessAnalystNotAuthor_recordsSlotButLeavesDocumentInReviewUntilOtherSlotsFilled() {
        User author = user(5L, "author-ba");
        User approvingBa = user(9L, "approving-ba");
        ClientProject project = project(10L, user(1L, "client-user"), null);
        RequirementDocument document = document(1L, RequirementDocumentType.BRD, project, author);
        List<RequirementDocumentApproval> slots = new ArrayList<>(List.of(
                new RequirementDocumentApproval(document, RoleCode.CLIENT),
                new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST),
                new RequirementDocumentApproval(document, RoleCode.DEVELOPER)));

        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(9L)).thenReturn(Optional.of(approvingBa));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(slots);
        when(approvalRepository.existsPriorBusinessAnalystApproval(10L)).thenReturn(false);
        when(userRoleRepository.findRoleCodesByUserId(9L)).thenReturn(List.of(RoleCode.BUSINESS_ANALYST));
        authenticateAs(9L, RoleCode.BUSINESS_ANALYST);

        RequirementDocument result = approvalService.approve(1L, 9L);

        assertThat(result.getStatus()).isEqualTo(RequirementDocumentStatus.IN_REVIEW);
        RequirementDocumentApproval baSlot = slots.stream()
                .filter(s -> s.getApproverRole() == RoleCode.BUSINESS_ANALYST).findFirst().orElseThrow();
        assertThat(baSlot.getApprovedBy()).isEqualTo(approvingBa);
    }

    @Test
    void approve_lastRemainingSlot_flipsDocumentStatusToApproved() {
        User author = user(5L, "author-ba");
        User developer = user(7L, "dev-uuid");
        ClientProject project = project(10L, null, developer);
        RequirementDocument document = document(1L, RequirementDocumentType.SRS, project, author);
        RequirementDocumentApproval baSlot = new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST);
        baSlot.setApprovedBy(user(9L, "approving-ba"));
        RequirementDocumentApproval devSlot = new RequirementDocumentApproval(document, RoleCode.DEVELOPER);
        List<RequirementDocumentApproval> slots = new ArrayList<>(List.of(baSlot, devSlot));

        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(7L)).thenReturn(Optional.of(developer));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(slots);
        when(approvalRepository.existsPriorBusinessAnalystApproval(10L)).thenReturn(true);
        authenticateAs(7L, RoleCode.DEVELOPER);

        RequirementDocument result = approvalService.approve(1L, 7L);

        assertThat(result.getStatus()).isEqualTo(RequirementDocumentStatus.APPROVED);
        assertThat(result.getApprovedBy()).isEqualTo(developer);
        verify(requirementDocumentRepository).save(document);
    }

    @Test
    void approve_authorHoldingBusinessAnalystRole_isBlockedFromApprovingOwnDocument_whenAnotherBaIsOnStaff() {
        User author = user(5L, "author-ba");
        RequirementDocument document = document(1L, RequirementDocumentType.SRS, project(10L, null, null), author);
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(5L)).thenReturn(Optional.of(author));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(new ArrayList<>(List.of(
                new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST),
                new RequirementDocumentApproval(document, RoleCode.DEVELOPER))));
        when(userRoleRepository.findRoleCodesByUserId(5L)).thenReturn(List.of(RoleCode.BUSINESS_ANALYST));
        // Another BA (id 6) is on staff besides the author (id 5) — the author must hand this off.
        when(userRoleRepository.findUserIdsByRoleCode(RoleCode.BUSINESS_ANALYST)).thenReturn(List.of(5L, 6L));
        authenticateAs(5L, RoleCode.BUSINESS_ANALYST);

        assertThatThrownBy(() -> approvalService.approve(1L, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    /** Regression test for a real deadlock found in manual testing: a team with exactly one BA on
     * staff who authors every document. Unconditionally blocking self-approval left the
     * BUSINESS_ANALYST slot — and everything gated on it, including developer auto-assignment —
     * permanently unfillable, since no other BA could ever exist to hand it to. */
    @Test
    void approve_authorHoldingBusinessAnalystRole_canSelfApprove_whenNoOtherBaIsOnStaff() {
        User author = user(5L, "author-ba");
        RequirementDocument document = document(1L, RequirementDocumentType.SRS, project(10L, null, null), author);
        RequirementDocumentApproval baSlot = new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST);
        RequirementDocumentApproval devSlot = new RequirementDocumentApproval(document, RoleCode.DEVELOPER);
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(5L)).thenReturn(Optional.of(author));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(new ArrayList<>(List.of(baSlot, devSlot)));
        when(approvalRepository.existsPriorBusinessAnalystApproval(10L)).thenReturn(false);
        when(userRoleRepository.findRoleCodesByUserId(5L)).thenReturn(List.of(RoleCode.BUSINESS_ANALYST));
        // Author (id 5) is the only BA on staff.
        when(userRoleRepository.findUserIdsByRoleCode(RoleCode.BUSINESS_ANALYST)).thenReturn(List.of(5L));

        authenticateAs(5L, RoleCode.BUSINESS_ANALYST);

        RequirementDocument result = approvalService.approve(1L, 5L);

        assertThat(baSlot.getApprovedBy()).isEqualTo(author);
        assertThat(result.getStatus()).isEqualTo(RequirementDocumentStatus.IN_REVIEW); // DEVELOPER slot still open
    }

    @Test
    void approve_clientOwningTheProject_fillsClientSlot() {
        User clientUser = user(3L, "client-uuid");
        ClientProject project = project(10L, clientUser, null);
        RequirementDocument document = document(1L, RequirementDocumentType.BRD, project, user(5L, "ba"));
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(3L)).thenReturn(Optional.of(clientUser));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(new ArrayList<>(List.of(
                new RequirementDocumentApproval(document, RoleCode.CLIENT),
                new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST),
                new RequirementDocumentApproval(document, RoleCode.DEVELOPER))));
        when(approvalRepository.existsPriorBusinessAnalystApproval(10L)).thenReturn(false);
        authenticateAs(3L, RoleCode.CLIENT);

        approvalService.approve(1L, 3L);

        verify(auditLogService).recordAfterCommit(eq(3L), eq("REQUIREMENT_DOCUMENT_APPROVAL_RECORDED"),
                any(), eq(1L), any(), any());
    }

    @Test
    void approve_developerNotAssignedToProject_isForbidden() {
        User someDeveloper = user(8L, "other-dev");
        User assignedDeveloper = user(7L, "assigned-dev");
        ClientProject project = project(10L, null, assignedDeveloper);
        RequirementDocument document = document(1L, RequirementDocumentType.SRS, project, user(5L, "ba"));
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(8L)).thenReturn(Optional.of(someDeveloper));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(new ArrayList<>(List.of(
                new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST),
                new RequirementDocumentApproval(document, RoleCode.DEVELOPER))));
        when(userRoleRepository.findRoleCodesByUserId(8L)).thenReturn(List.of(RoleCode.DEVELOPER));
        authenticateAs(8L, RoleCode.DEVELOPER);

        assertThatThrownBy(() -> approvalService.approve(1L, 8L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    void approve_callerWithNoApplicableSlot_isForbidden() {
        RequirementDocument document = document(1L, RequirementDocumentType.SRS, project(10L, null, null), user(5L, "ba"));
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user(99L, "stranger")));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(new ArrayList<>(List.of(
                new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST),
                new RequirementDocumentApproval(document, RoleCode.DEVELOPER))));
        when(userRoleRepository.findRoleCodesByUserId(99L)).thenReturn(List.of(RoleCode.STUDENT));
        authenticateAs(99L, RoleCode.STUDENT);

        assertThatThrownBy(() -> approvalService.approve(1L, 99L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    // ---- ADMIN fast-track ----

    @Test
    void approve_admin_fastTracksEveryPendingSlotAndApprovesDocumentInOneCall() {
        User admin = user(1L, "admin-uuid");
        RequirementDocument document = document(1L, RequirementDocumentType.SRS, project(10L, null, null), user(5L, "ba"));
        List<RequirementDocumentApproval> slots = new ArrayList<>(List.of(
                new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST),
                new RequirementDocumentApproval(document, RoleCode.DEVELOPER)));
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(slots);
        when(approvalRepository.existsPriorBusinessAnalystApproval(10L)).thenReturn(false);
        authenticateAs(1L, RoleCode.ADMIN);

        RequirementDocument result = approvalService.approve(1L, 1L);

        assertThat(slots).allMatch(s -> s.getApprovedBy() == admin);
        assertThat(result.getStatus()).isEqualTo(RequirementDocumentStatus.APPROVED);
    }

    @Test
    void approve_adminOnAlreadyFullyApprovedSlots_throwsBusinessRuleViolation() {
        RequirementDocument document = document(1L, RequirementDocumentType.SRS, project(10L, null, null), user(5L, "ba"));
        RequirementDocumentApproval baSlot = new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST);
        baSlot.setApprovedBy(user(9L, "ba2"));
        RequirementDocumentApproval devSlot = new RequirementDocumentApproval(document, RoleCode.DEVELOPER);
        devSlot.setApprovedBy(user(7L, "dev"));
        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin")));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(new ArrayList<>(List.of(baSlot, devSlot)));
        authenticateAs(1L, RoleCode.ADMIN);

        // status is IN_REVIEW (not yet flipped) but every slot is already filled — fast-track has nothing to do.
        assertThatThrownBy(() -> approvalService.approve(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    // ---- developer auto-assignment trigger ----

    @Test
    void approve_firstBusinessAnalystApprovalOfBrd_autoAssignsLeastBusyDeveloper() {
        User author = user(5L, "author-ba");
        User approvingBa = user(9L, "approving-ba");
        User leastBusyDev = user(20L, "dev-least-busy");
        ClientProject project = project(10L, user(1L, "client"), null);
        RequirementDocument document = document(1L, RequirementDocumentType.BRD, project, author);
        List<RequirementDocumentApproval> slots = new ArrayList<>(List.of(
                new RequirementDocumentApproval(document, RoleCode.CLIENT),
                new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST),
                new RequirementDocumentApproval(document, RoleCode.DEVELOPER)));

        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(9L)).thenReturn(Optional.of(approvingBa));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(slots);
        when(approvalRepository.existsPriorBusinessAnalystApproval(10L)).thenReturn(false);
        when(userRoleRepository.findRoleCodesByUserId(9L)).thenReturn(List.of(RoleCode.BUSINESS_ANALYST));
        when(staffAssignmentService.pickLeastBusy(RoleCode.DEVELOPER)).thenReturn(Optional.of(leastBusyDev));
        authenticateAs(9L, RoleCode.BUSINESS_ANALYST);

        approvalService.approve(1L, 9L);

        assertThat(project.getAssignedDeveloper()).isEqualTo(leastBusyDev);
        verify(clientProjectRepository).save(project);
    }

    @Test
    void approve_baApprovalOnProjectThatAlreadyHasADeveloper_doesNotReassign() {
        User author = user(5L, "author-ba");
        User approvingBa = user(9L, "approving-ba");
        User existingDeveloper = user(7L, "existing-dev");
        ClientProject project = project(10L, user(1L, "client"), existingDeveloper);
        RequirementDocument document = document(1L, RequirementDocumentType.BRD, project, author);
        List<RequirementDocumentApproval> slots = new ArrayList<>(List.of(
                new RequirementDocumentApproval(document, RoleCode.CLIENT),
                new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST),
                new RequirementDocumentApproval(document, RoleCode.DEVELOPER)));

        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(9L)).thenReturn(Optional.of(approvingBa));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(slots);
        when(approvalRepository.existsPriorBusinessAnalystApproval(10L)).thenReturn(false);
        when(userRoleRepository.findRoleCodesByUserId(9L)).thenReturn(List.of(RoleCode.BUSINESS_ANALYST));
        authenticateAs(9L, RoleCode.BUSINESS_ANALYST);

        approvalService.approve(1L, 9L);

        assertThat(project.getAssignedDeveloper()).isEqualTo(existingDeveloper);
        verify(staffAssignmentService, never()).pickLeastBusy(any());
    }

    @Test
    void approve_baApprovalOnSrs_doesNotTriggerDeveloperAutoAssign() {
        User author = user(5L, "author-ba");
        User approvingBa = user(9L, "approving-ba");
        ClientProject project = project(10L, null, null);
        RequirementDocument document = document(1L, RequirementDocumentType.SRS, project, author);
        List<RequirementDocumentApproval> slots = new ArrayList<>(List.of(
                new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST),
                new RequirementDocumentApproval(document, RoleCode.DEVELOPER)));

        when(requirementDocumentRepository.findWithAssociationsById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findById(9L)).thenReturn(Optional.of(approvingBa));
        when(approvalRepository.findByDocumentId(1L)).thenReturn(slots);
        when(approvalRepository.existsPriorBusinessAnalystApproval(10L)).thenReturn(false);
        when(userRoleRepository.findRoleCodesByUserId(9L)).thenReturn(List.of(RoleCode.BUSINESS_ANALYST));
        authenticateAs(9L, RoleCode.BUSINESS_ANALYST);

        approvalService.approve(1L, 9L);

        verify(staffAssignmentService, never()).pickLeastBusy(any());
    }

    // ---- pendingFor ----
    //
    // Regression coverage for a real bug found live: approve()'s self-approval exception for a
    // sole BA (see resolveCallerSlot's own Javadoc) was never mirrored here, so a solo BA's own
    // document — fully approvable via the API — simply never appeared in their own "Client
    // Project Documents" inbox. There was no test for pendingFor() at all before this; that's
    // exactly how the mismatch went unnoticed.

    @Test
    void pendingFor_soleBaOnStaff_seesTheirOwnAuthoredDocumentInInbox() {
        User solebA = user(5L, "sole-ba");
        RequirementDocument document = document(1L, RequirementDocumentType.BRD, project(10L, null, null), solebA);
        RequirementDocumentApproval baSlot = new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST);
        when(userRepository.findById(5L)).thenReturn(Optional.of(solebA));
        when(approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.CLIENT)).thenReturn(List.of());
        when(approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.DEVELOPER)).thenReturn(List.of());
        when(approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.BUSINESS_ANALYST)).thenReturn(List.of(baSlot));
        when(userRoleRepository.findRoleCodesByUserId(5L)).thenReturn(List.of(RoleCode.BUSINESS_ANALYST));
        // Author (id 5) is the only BA on staff.
        when(userRoleRepository.findUserIdsByRoleCode(RoleCode.BUSINESS_ANALYST)).thenReturn(List.of(5L));

        List<PendingApprovalResponse> pending = approvalService.pendingFor(5L);

        assertThat(pending).extracting(PendingApprovalResponse::documentId).containsExactly(1L);
    }

    @Test
    void pendingFor_anotherBaOnStaff_excludesTheirOwnAuthoredDocument() {
        User author = user(5L, "author-ba");
        RequirementDocument document = document(1L, RequirementDocumentType.BRD, project(10L, null, null), author);
        RequirementDocumentApproval baSlot = new RequirementDocumentApproval(document, RoleCode.BUSINESS_ANALYST);
        when(userRepository.findById(5L)).thenReturn(Optional.of(author));
        when(approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.CLIENT)).thenReturn(List.of());
        when(approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.DEVELOPER)).thenReturn(List.of());
        when(approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.BUSINESS_ANALYST)).thenReturn(List.of(baSlot));
        when(userRoleRepository.findRoleCodesByUserId(5L)).thenReturn(List.of(RoleCode.BUSINESS_ANALYST));
        // Another BA (id 6) is on staff besides the author (id 5).
        when(userRoleRepository.findUserIdsByRoleCode(RoleCode.BUSINESS_ANALYST)).thenReturn(List.of(5L, 6L));

        List<PendingApprovalResponse> pending = approvalService.pendingFor(5L);

        assertThat(pending).isEmpty();
    }

    @Test
    void pendingFor_client_seesOwnProjectsPendingClientSlot() {
        User clientUser = user(3L, "client-uuid");
        ClientProject project = project(10L, clientUser, null);
        RequirementDocument document = document(1L, RequirementDocumentType.BRD, project, user(5L, "ba"));
        RequirementDocumentApproval clientSlot = new RequirementDocumentApproval(document, RoleCode.CLIENT);
        when(userRepository.findById(3L)).thenReturn(Optional.of(clientUser));
        when(approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.CLIENT)).thenReturn(List.of(clientSlot));
        when(approvalRepository.findByApproverRoleAndApprovedByIsNull(RoleCode.DEVELOPER)).thenReturn(List.of());
        when(userRoleRepository.findRoleCodesByUserId(3L)).thenReturn(List.of(RoleCode.CLIENT));

        List<PendingApprovalResponse> pending = approvalService.pendingFor(3L);

        assertThat(pending).extracting(PendingApprovalResponse::documentId).containsExactly(1L);
    }
}
