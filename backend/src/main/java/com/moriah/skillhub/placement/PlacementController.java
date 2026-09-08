package com.moriah.skillhub.placement;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.placement.dto.PlacementCandidateOption;
import com.moriah.skillhub.placement.dto.PlacementResponse;
import com.moriah.skillhub.placement.dto.UpdatePlacementRequest;
import com.moriah.skillhub.placement.entity.PlacementStage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The client-placement pipeline. A {@code Placement} is created automatically when a recruitment
 * request is APPROVED (see {@code TalentService}); this controller only lists and advances it.
 * A CLIENT sees the placements they requested, a STUDENT the ones where they are the candidate,
 * HR_MANAGER / ADMIN see all — scoped in {@link PlacementService}. Per-stage write permission is
 * enforced there too ({@link PlacementStage} Javadoc).
 */
@RestController
@RequestMapping("/api/v1/placements")
@RequiredArgsConstructor
@Tag(name = "Placements")
public class PlacementController {

    private final PlacementService placementService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT','STUDENT','HR_MANAGER','ADMIN')")
    @Operation(summary = "List placements visible to the caller — optional stage filter")
    public ResponseEntity<ApiResponse<PageResponse<PlacementResponse>>> list(
            @RequestParam(required = false) PlacementStage stage,
            @CurrentUser Long callerUserId,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(placementService.list(stage, callerUserId, pageable)));
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('HR_MANAGER','ADMIN')")
    @Operation(summary = "Every client-shortlisted candidate as a picker option for HR letter "
            + "generation (recipient is chosen, never free-typed). `graduated` flags the ones who "
            + "have finished a batch.")
    public ResponseEntity<ApiResponse<java.util.List<PlacementCandidateOption>>> candidates() {
        return ResponseEntity.ok(ApiResponse.success(placementService.candidateOptions()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT','STUDENT','HR_MANAGER','ADMIN')")
    @Operation(summary = "One placement")
    public ResponseEntity<ApiResponse<PlacementResponse>> get(
            @PathVariable Long id, @CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(placementService.get(id, callerUserId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT','STUDENT','HR_MANAGER','ADMIN')")
    @Operation(summary = "Advance a placement's stage (forward-only; REJECTED from any non-terminal) "
            + "and merge stage fields into details. Per-stage ownership is enforced.")
    public ResponseEntity<ApiResponse<PlacementResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePlacementRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(placementService.update(id, request, callerUserId)));
    }
}
