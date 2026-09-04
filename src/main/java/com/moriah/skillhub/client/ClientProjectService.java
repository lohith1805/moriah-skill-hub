package com.moriah.skillhub.client;

import com.moriah.skillhub.batch.entity.Batch;
import com.moriah.skillhub.client.dto.ClientProjectProgressResponse;
import com.moriah.skillhub.client.dto.ClientProjectProgressResponse.SprintBurndown;
import com.moriah.skillhub.client.dto.ClientProjectResponse;
import com.moriah.skillhub.client.dto.CreateClientProjectRequest;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.client.entity.Client;
import com.moriah.skillhub.client.entity.ClientProject;
import com.moriah.skillhub.client.entity.ClientProjectStatus;
import com.moriah.skillhub.client.repository.ClientProjectRepository;
import com.moriah.skillhub.client.repository.ClientRepository;
import com.moriah.skillhub.common.exception.ErrorCode;
import com.moriah.skillhub.common.exception.ForbiddenOperationException;
import com.moriah.skillhub.common.exception.ResourceNotFoundException;
import com.moriah.skillhub.common.security.SecurityUtils;
import com.moriah.skillhub.sprint.SprintService;
import com.moriah.skillhub.sprint.dto.SprintProgressProjection;
import com.moriah.skillhub.sprint.entity.SprintStatus;
import com.moriah.skillhub.user.entity.RoleCode;
import com.moriah.skillhub.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * build-plan.md feature 21: "Clients submit scope" ({@link #create}, CLIENT-only) and "GET
 * /clients/projects/{id}/progress returns burndown and milestone completion for that client's
 * project only" ({@link #progress} — owner-CLIENT or BUSINESS_ANALYST/ADMIN staff oversight, the
 * same "owner or staff role" shape {@code BatchService#requireOwnerOrAdmin} already establishes,
 * reimplemented by hand here since {@code Client}/{@code ClientProject} aren't {@code Batch}).
 */
@Service
@RequiredArgsConstructor
public class ClientProjectService {

    private final ClientProjectRepository clientProjectRepository;
    private final ClientRepository clientRepository;
    private final SprintService sprintService;

    /** Resolves the caller's own {@code client_id} via {@code clients.user_id = callerUserId}
     * (build-plan.md feature 21 decision). A CLIENT user with no linked {@code clients} row is a
     * data-integrity impossibility given {@code ClientService#create}'s provisioning flow — every
     * portal login it creates is linked in the same transaction — but this guards it anyway with
     * a clear {@link ErrorCode#CLIENT_NOT_FOUND}, not an NPE. */
    @Transactional
    public ClientProjectResponse create(CreateClientProjectRequest request, Long callerUserId) {
        Client client = clientRepository.findByUserId(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLIENT_NOT_FOUND, callerUserId));

        ClientProject project = new ClientProject();
        project.setClient(client);
        project.setTitle(request.title());
        project.setScopeDescription(request.scopeDescription());
        project.setBudgetRange(request.budgetRange());
        project.setStatus(ClientProjectStatus.SUBMITTED);
        project.setSubmittedAt(Instant.now());
        clientProjectRepository.save(project);

        return toResponse(project);
    }

    /**
     * {@code GET /api/v1/clients/projects} — a CLIENT sees only their own company's submissions;
     * a BUSINESS_ANALYST / ADMIN sees every client's (staff oversight, the same unconditional
     * split {@link #progress} already uses). A CLIENT with no linked {@code clients} row gets an
     * empty page, not a 404 — nothing to show is not an error on a list.
     */
    @Transactional(readOnly = true)
    public PageResponse<ClientProjectResponse> list(Long callerUserId, ClientProjectStatus status, Pageable pageable) {
        List<String> roles = SecurityUtils.currentUserRoles();
        boolean isStaff = roles.contains(RoleCode.BUSINESS_ANALYST.name()) || roles.contains(RoleCode.ADMIN.name());

        Long clientId = null;
        if (!isStaff) {
            clientId = clientRepository.findByUserId(callerUserId).map(Client::getId).orElse(null);
            if (clientId == null) {
                return PageResponse.from(org.springframework.data.domain.Page.<ClientProjectResponse>empty(pageable));
            }
        }
        return PageResponse.from(clientProjectRepository.search(clientId, status, pageable).map(this::toResponse));
    }

    /** build-plan.md feature 21 "Verify": "A client requesting another client's project gets
     * 403." {@code target_batch_id IS NULL} returns a zeroed shape rather than an error (the
     * feature's own decision — "if target_batch_id is null ... return an empty/zeroed progress
     * shape, not an error"). */
    @Transactional(readOnly = true)
    public ClientProjectProgressResponse progress(Long clientProjectId, Long callerUserId) {
        ClientProject project = clientProjectRepository.findWithClientById(clientProjectId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CLIENT_PROJECT_NOT_FOUND, clientProjectId));
        requireOwningClientOrStaff(callerUserId, project);

        Batch targetBatch = project.getTargetBatch();
        if (targetBatch == null) {
            return ClientProjectProgressResponse.empty(project.getId(), project.getTitle());
        }

        List<SprintProgressProjection> sprints = sprintService.progressForBatch(targetBatch.getId());
        long totalSprints = sprints.size();
        long completedSprints = sprints.stream().filter(s -> s.status() == SprintStatus.COMPLETED).count();
        double milestoneCompletion = totalSprints == 0 ? 0.0 : (double) completedSprints / totalSprints;

        List<SprintBurndown> burndown = sprints.stream()
                .map(s -> new SprintBurndown(
                        s.sprintId(),
                        s.sprintNumber(),
                        s.status().name(),
                        s.plannedPoints() == null ? 0 : s.plannedPoints(),
                        s.completedPoints() == null ? 0 : s.completedPoints()))
                .toList();

        return new ClientProjectProgressResponse(project.getId(), project.getTitle(), targetBatch.getId(),
                milestoneCompletion, burndown);
    }

    /** No ownership check at all for {@code BUSINESS_ANALYST}/{@code ADMIN} — staff oversight is
     * unconditional, matching {@code BatchService.requireOwnerOrAdmin}'s own ADMIN-bypasses-
     * ownership shape. {@code SecurityUtils.currentUserRoles()} — no DB round trip, same
     * technique {@code BatchService}/{@code TaskService} already use for this exact kind of
     * check. */
    private void requireOwningClientOrStaff(Long callerUserId, ClientProject project) {
        List<String> roles = SecurityUtils.currentUserRoles();
        boolean isStaff = roles.contains(RoleCode.BUSINESS_ANALYST.name()) || roles.contains(RoleCode.ADMIN.name());
        if (isStaff) {
            return;
        }
        User owner = project.getClient().getUser();
        if (owner == null || !Objects.equals(owner.getId(), callerUserId)) {
            throw new ForbiddenOperationException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }

    private ClientProjectResponse toResponse(ClientProject project) {
        return new ClientProjectResponse(
                project.getId(),
                project.getClient().getId(),
                project.getClient().getCompanyName(),
                project.getTitle(),
                project.getScopeDescription(),
                project.getBudgetRange(),
                project.getTargetBatch() == null ? null : project.getTargetBatch().getId(),
                project.getStatus(),
                project.getSubmittedAt());
    }
}
