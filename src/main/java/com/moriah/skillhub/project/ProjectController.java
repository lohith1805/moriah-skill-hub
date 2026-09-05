package com.moriah.skillhub.project;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.project.dto.CreateProjectRequest;
import com.moriah.skillhub.project.dto.ProjectAssetResponse;
import com.moriah.skillhub.project.dto.ProjectResponse;
import com.moriah.skillhub.project.dto.UpdateProjectRequest;
import com.moriah.skillhub.project.entity.AssetType;
import com.moriah.skillhub.project.entity.ProjectDifficulty;
import com.moriah.skillhub.project.entity.ProjectStatus;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** build-plan.md feature 15. Mutation endpoints ({@code create}/{@code update}/{@code
 * addAsset}/{@code publish}) are {@code DEVELOPER}/{@code ADMIN} only, ownership enforced in the
 * service layer via {@code ProjectService#requireCreatorOrAdmin} (same reasoning {@code
 * SprintController}'s own Javadoc gives). {@code list} is open to every role — status filtering
 * beyond {@code PUBLISHED} is restricted to {@code ADMIN} inside the service, not here. Bug
 * challenges live on {@link ChallengeController}, matching architecture.md's own two-controller
 * split for this package. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Projects")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping("/api/v1/projects")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "Author a new project (always created DRAFT)")
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @Valid @RequestBody CreateProjectRequest request, @CurrentUser Long callerUserId) {

        ProjectResponse response = projectService.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/projects")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN','TRAINER_PM','STUDENT')")
    @Operation(summary = "Browse projects — non-admin callers always see PUBLISHED only")
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> list(
            @RequestParam(required = false) ProjectDifficulty difficulty,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String track,
            @PageableDefault(size = 20) Pageable pageable,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(
                projectService.list(callerUserId, difficulty, domain, status, track, pageable)));
    }

    @PutMapping("/api/v1/projects/{id}")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "Edit a DRAFT project in place, or bump a PUBLISHED/ARCHIVED one into a new DRAFT version")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateProjectRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(projectService.update(callerUserId, id, request)));
    }

    @PostMapping("/api/v1/projects/{id}/assets")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "Attach an asset — exactly one of file or externalUrl")
    public ResponseEntity<ApiResponse<ProjectAssetResponse>> addAsset(
            @PathVariable Long id,
            @RequestParam AssetType assetType,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String externalUrl,
            @RequestParam(required = false) Integer sortOrder,
            @CurrentUser Long callerUserId) {

        ProjectAssetResponse response = projectService.addAsset(
                callerUserId, id, assetType, title, file, externalUrl, sortOrder);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/api/v1/projects/{id}/publish")
    @PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
    @Operation(summary = "DRAFT -> PUBLISHED — only a PUBLISHED project can attach to a task")
    public ResponseEntity<ApiResponse<ProjectResponse>> publish(
            @PathVariable Long id, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(projectService.publish(callerUserId, id)));
    }
}
