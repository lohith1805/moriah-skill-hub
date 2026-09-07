package com.moriah.skillhub.client;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.client.dto.AssignClientProjectRequest;
import com.moriah.skillhub.client.dto.ClientProjectProgressResponse;
import com.moriah.skillhub.client.dto.ClientProjectResponse;
import com.moriah.skillhub.client.dto.CreateClientProjectRequest;
import com.moriah.skillhub.client.entity.Client;
import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.repository.ClientProjectRepository;
import com.moriah.skillhub.client.repository.ClientRepository;
import com.moriah.skillhub.common.audit.AuditLogService;
import com.moriah.skillhub.common.exception.BusinessException;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.AuthenticatedPrincipal;
import com.moriah.skillhub.sprint.SprintService;
import com.moriah.skillhub.sprint.dto.SprintProgressProjection;
import com.moriah.skillhub.sprint.entity.SprintStatus;
import com.moriah.skillhub.sprint.entity.TaskStatus;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import com.moriah.skillhub.user.repository.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** build-plan.md feature 21: "Clients submit scope" and "GET /clients/projects/{id}/progress
 * returns burndown and milestone completion for that client's project only... A client
 * requesting another client's project gets 403." {@link ClientProjectService} reads {@code
 * SecurityUtils.currentUserRoles()} off the real {@code SecurityContextHolder} (no abstraction
 * to mock) — same pattern {@code BatchServiceTest} already established for {@code
 * BatchService#requireOwnerOrAdmin}. */
@ExtendWith(MockitoExtension.class)
class ClientProjectServiceTest {

    @Mock
    private ClientProjectRepository clientProjectRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private com.moriah.skillhub.user.repository.UserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private SprintService sprintService;
    @Mock
    private StaffAssignmentService staffAssignmentService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ClientProjectService clientProjectService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Client client(long id, Long ownerUserId) {
        Client client = new Client();
        client.setId(id);
        client.setCompanyName("Acme Corp " + id);
        if (ownerUserId != null) {
            User user = new User();
            user.setId(ownerUserId);
            user.setUuid("client-user-" + ownerUserId);
            client.setUser(user);
        }
        return client;
    }

    private ClientProject project(long id, Client client, Batch targetBatch) {
        ClientProject project = new ClientProject();
        project.setId(id);
        project.setClient(client);
        project.setTitle("Storefront Revamp");
        project.setTargetBatch(targetBatch);
        return project;
    }

    private void authenticateAs(long userId, List<String> roles) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, "uuid-" + userId, roles);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }

    @Test
    void create_resolvesCallersOwnClientAndSaves() {
        Client client = client(1L, 50L);
        when(clientRepository.findByUserId(50L)).thenReturn(Optional.of(client));
        when(clientProjectRepository.save(org.mockito.ArgumentMatchers.any(ClientProject.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateClientProjectRequest request = new CreateClientProjectRequest(
                "New Storefront", "Build a storefront.", "10k-20k", null);

        ClientProjectResponse response = clientProjectService.create(request, 50L);

        assertThat(response.title()).isEqualTo("New Storefront");
        assertThat(response.clientId()).isEqualTo(1L);
    }

    @Test
    void create_autoAssignsLeastBusyBa() {
        Client client = client(1L, 50L);
        when(clientRepository.findByUserId(50L)).thenReturn(Optional.of(client));
        when(clientProjectRepository.save(any(ClientProject.class))).thenAnswer(inv -> inv.getArgument(0));
        User ba = new User();
        ba.setId(7L);
        ba.setUuid("ba-uuid-7");
        when(staffAssignmentService.pickLeastBusy(RoleCode.BUSINESS_ANALYST)).thenReturn(Optional.of(ba));

        CreateClientProjectRequest request = new CreateClientProjectRequest(
                "New Storefront", "Build a storefront.", "10k-20k", "Discuss on kickoff call.");

        ClientProjectResponse response = clientProjectService.create(request, 50L);

        assertThat(response.assignedBaUuid()).isEqualTo("ba-uuid-7");
        assertThat(response.additionalNotes()).isEqualTo("Discuss on kickoff call.");
    }

    @Test
    void create_callerHasNoLinkedClientRow_selfHealsFromUser() {
        User user = new User();
        user.setId(50L);
        user.setFullName("Acme Contact");
        user.setEmail("contact@acme.test");
        when(clientRepository.findByUserId(50L)).thenReturn(Optional.empty());
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));
        when(clientRepository.save(org.mockito.ArgumentMatchers.any(Client.class)))
                .thenAnswer(inv -> { Client c = inv.getArgument(0); c.setId(9L); return c; });
        when(clientProjectRepository.save(org.mockito.ArgumentMatchers.any(ClientProject.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateClientProjectRequest request = new CreateClientProjectRequest("T", "S", null, null);
        ClientProjectResponse response = clientProjectService.create(request, 50L);

        assertThat(response.title()).isEqualTo("T");
        assertThat(response.clientName()).isEqualTo("Acme Contact");
        org.mockito.Mockito.verify(clientRepository).save(org.mockito.ArgumentMatchers.any(Client.class));
    }

    @Test
    void progress_notFound_throwsClientProjectNotFound() {
        when(clientProjectRepository.findWithClientById(404L)).thenReturn(Optional.empty());
        authenticateAs(50L, List.of("CLIENT"));

        assertThatThrownBy(() -> clientProjectService.progress(404L, 50L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLIENT_PROJECT_NOT_FOUND);
    }

    @Test
    void progress_noTargetBatch_returnsZeroedShape() {
        Client client = client(1L, 50L);
        ClientProject project = project(10L, client, null);
        when(clientProjectRepository.findWithClientById(10L)).thenReturn(Optional.of(project));
        authenticateAs(50L, List.of("CLIENT"));

        ClientProjectProgressResponse response = clientProjectService.progress(10L, 50L);

        assertThat(response.targetBatchId()).isNull();
        assertThat(response.milestoneCompletionFraction()).isZero();
        assertThat(response.burndown()).isEmpty();
    }

    @Test
    void progress_ownerClient_returnsBurndownAndMilestoneFraction() {
        Client client = client(1L, 50L);
        Batch batch = new Batch();
        batch.setId(200L);
        ClientProject project = project(10L, client, batch);
        when(clientProjectRepository.findWithClientById(10L)).thenReturn(Optional.of(project));
        when(sprintService.progressForBatch(200L)).thenReturn(List.of(
                new SprintProgressProjection(1L, 1, SprintStatus.COMPLETED, 10, 10),
                new SprintProgressProjection(2L, 2, SprintStatus.ACTIVE, 8, 3)));
        when(sprintService.taskStatusCountsForBatch(200L)).thenReturn(Map.of());
        authenticateAs(50L, List.of("CLIENT"));

        ClientProjectProgressResponse response = clientProjectService.progress(10L, 50L);

        assertThat(response.targetBatchId()).isEqualTo(200L);
        assertThat(response.milestoneCompletionFraction()).isEqualTo(0.5);
        assertThat(response.burndown()).hasSize(2);
        assertThat(response.burndown().get(0).sprintStatus()).isEqualTo("COMPLETED");
        assertThat(response.burndown().get(0).completedPoints()).isEqualTo(10);
        assertThat(response.burndown().get(1).completedPoints()).isEqualTo(3);
    }

    /** FRS MSH-FR-PM-02/MSH-FR-BA-03: task-status counts merged in alongside the story-point
     * burndown, keyed by sprint id. A sprint absent from the task-counts map (sprint 2 here) gets
     * an empty map, not null or a missing field. */
    @Test
    void progress_ownerClient_mergesTaskStatusCountsBySprintId() {
        Client client = client(1L, 50L);
        Batch batch = new Batch();
        batch.setId(200L);
        ClientProject project = project(10L, client, batch);
        when(clientProjectRepository.findWithClientById(10L)).thenReturn(Optional.of(project));
        when(sprintService.progressForBatch(200L)).thenReturn(List.of(
                new SprintProgressProjection(1L, 1, SprintStatus.COMPLETED, 10, 10),
                new SprintProgressProjection(2L, 2, SprintStatus.ACTIVE, 8, 3)));
        when(sprintService.taskStatusCountsForBatch(200L)).thenReturn(Map.of(
                1L, Map.of(TaskStatus.COMPLETED, 4L, TaskStatus.REJECTED, 1L)));
        authenticateAs(50L, List.of("CLIENT"));

        ClientProjectProgressResponse response = clientProjectService.progress(10L, 50L);

        assertThat(response.burndown().get(0).taskStatusCounts())
                .containsEntry("COMPLETED", 4L)
                .containsEntry("REJECTED", 1L);
        assertThat(response.burndown().get(1).taskStatusCounts()).isEmpty();
    }

    @Test
    void progress_nonOwningClient_throwsForbidden() {
        Client client = client(1L, 50L);
        ClientProject project = project(10L, client, null);
        when(clientProjectRepository.findWithClientById(10L)).thenReturn(Optional.of(project));
        authenticateAs(99L, List.of("CLIENT"));

        assertThatThrownBy(() -> clientProjectService.progress(10L, 99L))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    void progress_businessAnalyst_bypassesOwnershipCheck() {
        Client client = client(1L, 50L);
        ClientProject project = project(10L, client, null);
        when(clientProjectRepository.findWithClientById(10L)).thenReturn(Optional.of(project));
        authenticateAs(999L, List.of("BUSINESS_ANALYST"));

        ClientProjectProgressResponse response = clientProjectService.progress(10L, 999L);

        assertThat(response.clientProjectId()).isEqualTo(10L);
    }

    @Test
    void progress_admin_bypassesOwnershipCheck() {
        Client client = client(1L, 50L);
        ClientProject project = project(10L, client, null);
        when(clientProjectRepository.findWithClientById(10L)).thenReturn(Optional.of(project));
        authenticateAs(999L, List.of("ADMIN"));

        ClientProjectProgressResponse response = clientProjectService.progress(10L, 999L);

        assertThat(response.clientProjectId()).isEqualTo(10L);
    }

    @Test
    void list_baWithoutAllProjects_scopesToOwnAssignmentPlusUnassigned() {
        authenticateAs(20L, List.of("BUSINESS_ANALYST"));
        when(clientProjectRepository.search(eq(null), eq(null), eq(20L), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        clientProjectService.list(20L, null, false, org.springframework.data.domain.PageRequest.of(0, 20));

        org.mockito.Mockito.verify(clientProjectRepository).search(eq(null), eq(null), eq(20L), any());
    }

    @Test
    void list_baWithAllProjects_seesEverything() {
        authenticateAs(20L, List.of("BUSINESS_ANALYST"));
        when(clientProjectRepository.search(eq(null), eq(null), eq(null), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        clientProjectService.list(20L, null, true, org.springframework.data.domain.PageRequest.of(0, 20));

        org.mockito.Mockito.verify(clientProjectRepository).search(eq(null), eq(null), eq(null), any());
    }

    @Test
    void assign_setsBaAndDeveloper_whenBothHoldTheRightRole() {
        Client client = client(1L, 50L);
        ClientProject project = project(10L, client, null);
        when(clientProjectRepository.findById(10L)).thenReturn(Optional.of(project));
        User ba = new User();
        ba.setId(20L);
        ba.setUuid("ba-uuid");
        User dev = new User();
        dev.setId(21L);
        dev.setUuid("dev-uuid");
        when(userRepository.findByUuid("ba-uuid")).thenReturn(Optional.of(ba));
        when(userRepository.findByUuid("dev-uuid")).thenReturn(Optional.of(dev));
        when(userRoleRepository.findRoleCodesByUserId(20L)).thenReturn(List.of(RoleCode.BUSINESS_ANALYST));
        when(userRoleRepository.findRoleCodesByUserId(21L)).thenReturn(List.of(RoleCode.DEVELOPER));

        ClientProjectResponse response = clientProjectService.assign(10L,
                new AssignClientProjectRequest("ba-uuid", "dev-uuid"), 4L);

        assertThat(response.assignedBaUuid()).isEqualTo("ba-uuid");
        assertThat(response.assignedDeveloperUuid()).isEqualTo("dev-uuid");
    }

    @Test
    void assign_wrongRole_throwsValidationFailed() {
        Client client = client(1L, 50L);
        ClientProject project = project(10L, client, null);
        when(clientProjectRepository.findById(10L)).thenReturn(Optional.of(project));
        User notABa = new User();
        notABa.setId(22L);
        notABa.setUuid("student-uuid");
        when(userRepository.findByUuid("student-uuid")).thenReturn(Optional.of(notABa));
        when(userRoleRepository.findRoleCodesByUserId(22L)).thenReturn(List.of(RoleCode.STUDENT));

        assertThatThrownBy(() -> clientProjectService.assign(10L,
                new AssignClientProjectRequest("student-uuid", null), 4L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void assign_neitherFieldProvided_throwsValidationFailed() {
        Client client = client(1L, 50L);
        ClientProject project = project(10L, client, null);
        when(clientProjectRepository.findById(10L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> clientProjectService.assign(10L,
                new AssignClientProjectRequest(null, null), 4L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }
}
