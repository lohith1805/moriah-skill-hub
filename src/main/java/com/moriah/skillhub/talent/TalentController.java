package com.moriah.skillhub.talent;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.talent.dto.CreateRecruitmentRequestRequest;
import com.moriah.skillhub.talent.dto.DecideRecruitmentRequestRequest;
import com.moriah.skillhub.talent.dto.RecruitmentRequestResponse;
import com.moriah.skillhub.talent.dto.TalentPoolCandidateResponse;
import com.moriah.skillhub.talent.entity.RecruitmentRequestStatus;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client Talent Pool + recruitment requests (gap B1.9). One controller for both concerns.
 * Browse: CLIENT / ADMIN / HR_MANAGER / LEAD_GEN. Create a request: CLIENT. List: CLIENT (own)
 * or ADMIN/HR_MANAGER (all — scoped in the service). Decide: ADMIN / HR_MANAGER.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Talent")
public class TalentController {

    private final TalentService talentService;

    @GetMapping("/talent-pool")
    @PreAuthorize("hasAnyRole('CLIENT','ADMIN','HR_MANAGER','LEAD_GEN')")
    @Operation(summary = "Browse candidate profiles — optional search (name/title) and skill filters")
    public ResponseEntity<ApiResponse<PageResponse<TalentPoolCandidateResponse>>> browse(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String skill,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(talentService.browse(search, skill, pageable)));
    }

    @PostMapping("/recruitment-requests")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Request to recruit a candidate — lands PENDING for ADMIN/HR review")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Request submitted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not a CLIENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No user with this candidateUuid")
    })
    public ResponseEntity<ApiResponse<RecruitmentRequestResponse>> createRequest(
            @Valid @RequestBody CreateRecruitmentRequestRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(talentService.createRequest(request, callerUserId)));
    }

    @GetMapping("/recruitment-requests")
    @PreAuthorize("hasAnyRole('CLIENT','ADMIN','HR_MANAGER')")
    @Operation(summary = "List recruitment requests — a CLIENT sees their own, ADMIN/HR_MANAGER see all")
    public ResponseEntity<ApiResponse<PageResponse<RecruitmentRequestResponse>>> listRequests(
            @RequestParam(required = false) RecruitmentRequestStatus status,
            @CurrentUser Long callerUserId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(talentService.listRequests(status, callerUserId, pageable)));
    }

    @PutMapping("/recruitment-requests/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    @Operation(summary = "Approve or reject a PENDING recruitment request")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Decision recorded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "status is not APPROVED or REJECTED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not ADMIN/HR_MANAGER"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No request with this id"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Request has already been decided")
    })
    public ResponseEntity<ApiResponse<RecruitmentRequestResponse>> decide(
            @PathVariable Long id, @Valid @RequestBody DecideRecruitmentRequestRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(talentService.decide(id, request, callerUserId)));
    }
}
