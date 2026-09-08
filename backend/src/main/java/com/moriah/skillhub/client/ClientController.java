package com.moriah.skillhub.client;

import com.moriah.skillhub.client.dto.ClientProjectProgressResponse;
import com.moriah.skillhub.client.dto.ClientProjectResponse;
import com.moriah.skillhub.client.dto.ClientResponse;
import com.moriah.skillhub.client.dto.CreateClientProjectRequest;
import com.moriah.skillhub.client.dto.CreateClientRequest;
import com.moriah.skillhub.client.entity.ClientProjectStatus;
import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /clients} is ADMIN-only (client companies are provisioned, never self-registered
 * — build-plan.md feature 21). {@code POST /clients/projects} is CLIENT-only (their own scope
 * submission). {@code GET /clients/projects/{id}/progress} allows CLIENT (their own project —
 * {@link ClientProjectService} enforces ownership) or BUSINESS_ANALYST/ADMIN (any project, staff
 * oversight) at the role gate; the finer-grained "is this actually your project" check happens
 * in the service, the same split {@code CertificateController}'s issue/revoke role gate plus
 * {@code BatchService#requireOwnerOrAdmin} ownership check already establishes. */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Tag(name = "Clients")
public class ClientController {

    private final ClientService clientService;
    private final ClientProjectService clientProjectService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Provision a client company, optionally with a portal login",
            description = "Returns 409 EMAIL_ALREADY_REGISTERED if provisionPortalLogin=true and the email is already a user")
    public ResponseEntity<ApiResponse<ClientResponse>> create(@Valid @RequestBody CreateClientRequest request,
            @CurrentUser Long callerUserId) {
        ClientResponse response = clientService.create(request, callerUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER','BUSINESS_ANALYST')")
    @Operation(summary = "Every ACTIVE client company — the picker list for HR letter generation "
            + "(so the recruiting-company name is chosen, never free-typed)")
    public ResponseEntity<ApiResponse<java.util.List<ClientResponse>>> listClients() {
        return ResponseEntity.ok(ApiResponse.success(clientService.listActive()));
    }

    @PostMapping("/projects")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Submit a new project scope as the caller's own client company",
            description = "Returns 404 CLIENT_NOT_FOUND if the caller has no linked clients row")
    public ResponseEntity<ApiResponse<ClientProjectResponse>> createProject(
            @Valid @RequestBody CreateClientProjectRequest request, @CurrentUser Long callerUserId) {
        ClientProjectResponse response = clientProjectService.create(request, callerUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/projects")
    @PreAuthorize("hasAnyRole('CLIENT','BUSINESS_ANALYST','ADMIN')")
    @Operation(summary = "List client project submissions — a CLIENT sees only their own company's; "
            + "a BUSINESS_ANALYST / ADMIN sees every client's")
    public ResponseEntity<ApiResponse<PageResponse<ClientProjectResponse>>> listProjects(
            @RequestParam(required = false) ClientProjectStatus status,
            @RequestParam(defaultValue = "false") boolean allProjects,
            @CurrentUser Long callerUserId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                clientProjectService.list(callerUserId, status, allProjects, pageable)));
    }

    @GetMapping("/projects/{id}/progress")
    @PreAuthorize("hasAnyRole('CLIENT','BUSINESS_ANALYST','ADMIN')")
    @Operation(summary = "Burndown and milestone completion for one client project — never another client's data",
            description = "Returns 403 for a CLIENT requesting another client's project; 404 if the project does not exist")
    public ResponseEntity<ApiResponse<ClientProjectProgressResponse>> progress(@PathVariable Long id,
            @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(clientProjectService.progress(id, callerUserId)));
    }
}
