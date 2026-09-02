package com.moriah.skillhub.resource;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.resource.dto.CreateResourceRequest;
import com.moriah.skillhub.resource.dto.LearningResourceResponse;
import com.moriah.skillhub.resource.dto.UpdateResourceRequest;
import com.moriah.skillhub.resource.entity.ResourceCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resource Library (gap B1.6). Browsing ({@code GET}) is open to any authenticated user;
 * curation ({@code POST}/{@code PUT}/{@code DELETE}) is staff-only, and {@code PUT}/{@code
 * DELETE} additionally require the caller to be the resource's creator or an {@code ADMIN}
 * (enforced in {@link ResourceService}). Not in {@code SecurityConfig.PUBLIC_PATHS}.
 */
@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
@Tag(name = "Resources")
public class ResourceController {

    private static final String CURATOR_ROLES = "hasAnyRole('TRAINER_PM','DEVELOPER','BUSINESS_ANALYST','ADMIN')";

    private final ResourceService resourceService;

    @GetMapping
    @Operation(summary = "Browse the resource library — optional category / search filters. "
            + "includeInactive is honoured only for curator roles.")
    public ResponseEntity<ApiResponse<PageResponse<LearningResourceResponse>>> list(
            @RequestParam(required = false) ResourceCategory category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                resourceService.list(includeInactive, category, search, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One resource by id")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resource"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No resource with this id")
    })
    public ResponseEntity<ApiResponse<LearningResourceResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(resourceService.get(id)));
    }

    @PostMapping
    @PreAuthorize(CURATOR_ROLES)
    @Operation(summary = "Add a resource to the library")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Resource created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not a curator role")
    })
    public ResponseEntity<ApiResponse<LearningResourceResponse>> create(
            @Valid @RequestBody CreateResourceRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(resourceService.create(request, callerUserId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CURATOR_ROLES)
    @Operation(summary = "Edit a resource — creator or ADMIN only")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resource updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the creator and not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No resource with this id")
    })
    public ResponseEntity<ApiResponse<LearningResourceResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateResourceRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(resourceService.update(id, request, callerUserId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CURATOR_ROLES)
    @Operation(summary = "Deactivate a resource (is_active = false) — creator or ADMIN only; never row-deletes")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resource deactivated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the creator and not an ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No resource with this id")
    })
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @CurrentUser Long callerUserId) {
        resourceService.deactivate(id, callerUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
