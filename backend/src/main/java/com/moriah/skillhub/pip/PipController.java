package com.moriah.skillhub.pip;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.pip.dto.CreatePipMilestoneRequest;
import com.moriah.skillhub.pip.dto.CreatePipRecordRequest;
import com.moriah.skillhub.pip.dto.PipMilestoneResponse;
import com.moriah.skillhub.pip.dto.PipProgressResponse;
import com.moriah.skillhub.pip.dto.PipRecordResponse;
import com.moriah.skillhub.pip.dto.PipRuleResponse;
import com.moriah.skillhub.pip.dto.ReviewPipRequest;
import com.moriah.skillhub.pip.dto.UpdatePipRuleRequest;
import com.moriah.skillhub.pip.entity.PipRuleCode;
import com.moriah.skillhub.pip.entity.PipStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** build-plan.md feature 17. {@code me} is {@code STUDENT}-only (their own open record); every
 * other read ({@code list}/{@code rules}) is open to {@code TRAINER_PM}/{@code HR_MANAGER}/{@code
 * ADMIN} — the three roles notified on every trigger. Mutations ({@code completeMilestone}/{@code
 * review}) are {@code TRAINER_PM}/{@code ADMIN}, ownership enforced in the service layer via
 * {@code BatchService#requireOwnerOrAdmin} (same reasoning {@code SprintController}'s own Javadoc
 * gives). {@code PUT /pip/rules/{code}} is {@code ADMIN} only — build-plan.md: "thresholds are
 * config, not a deployment." */
@RestController
@RequiredArgsConstructor
@Tag(name = "PIP")
public class PipController {

    private final PipService pipService;
    private final PipEvaluationService pipEvaluationService;

    @GetMapping("/api/v1/pip/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "The caller's own currently-open PIP record")
    public ResponseEntity<ApiResponse<PipRecordResponse>> me(@CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(pipService.me(callerUserId)));
    }

    @GetMapping("/api/v1/pip/me/progress")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "The caller's own recovery-progress panel")
    public ResponseEntity<ApiResponse<PipProgressResponse>> myProgress(@CurrentUser Long callerUserId) {
        return ResponseEntity.ok(ApiResponse.success(pipService.myProgress(callerUserId)));
    }

    @PostMapping("/api/v1/pip")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Raise a PIP by hand for a qualitative concern the nightly rules miss")
    public ResponseEntity<ApiResponse<PipRecordResponse>> createManual(
            @Valid @RequestBody CreatePipRecordRequest request, @CurrentUser Long callerUserId) {

        PipRecordResponse response = pipEvaluationService.createManual(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/pip")
    @PreAuthorize("hasAnyRole('TRAINER_PM','HR_MANAGER','ADMIN')")
    @Operation(summary = "Browse PIP records")
    public ResponseEntity<ApiResponse<PageResponse<PipRecordResponse>>> list(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) PipStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(pipService.list(batchId, status, pageable)));
    }

    @PostMapping("/api/v1/pip/{id}/milestones")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Add a recovery task to a PIP record's checklist")
    public ResponseEntity<ApiResponse<PipMilestoneResponse>> addMilestone(
            @PathVariable Long id, @Valid @RequestBody CreatePipMilestoneRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(pipService.addMilestone(callerUserId, id, request)));
    }

    @DeleteMapping("/api/v1/pip/{id}/milestones/{milestoneId}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Remove a still-pending recovery task")
    public ResponseEntity<ApiResponse<Void>> deleteMilestone(
            @PathVariable Long id, @PathVariable Long milestoneId, @CurrentUser Long callerUserId) {

        pipService.deleteMilestone(callerUserId, id, milestoneId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/api/v1/pip/{id}/milestones/{milestoneId}/complete")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Mark a PIP milestone complete")
    public ResponseEntity<ApiResponse<PipMilestoneResponse>> completeMilestone(
            @PathVariable Long id, @PathVariable Long milestoneId, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(pipService.completeMilestone(callerUserId, id, milestoneId)));
    }

    @GetMapping("/api/v1/pip/{id}/progress")
    @PreAuthorize("hasAnyRole('TRAINER_PM','HR_MANAGER','ADMIN')")
    @Operation(summary = "The recovery-progress panel for one PIP record")
    public ResponseEntity<ApiResponse<PipProgressResponse>> progress(
            @PathVariable Long id, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(pipService.progress(callerUserId, id)));
    }

    @PostMapping("/api/v1/pip/{id}/review")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Day-15 review — CLEARED requires task completion >= 85% and no unsatisfactory reviews")
    public ResponseEntity<ApiResponse<PipRecordResponse>> review(
            @PathVariable Long id, @Valid @RequestBody ReviewPipRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(pipService.review(callerUserId, id, request)));
    }

    @GetMapping("/api/v1/pip/rules")
    @PreAuthorize("hasAnyRole('TRAINER_PM','HR_MANAGER','ADMIN')")
    @Operation(summary = "The six PIP rules and their current thresholds")
    public ResponseEntity<ApiResponse<List<PipRuleResponse>>> rules() {
        return ResponseEntity.ok(ApiResponse.success(pipService.rules()));
    }

    @PutMapping("/api/v1/pip/rules/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a PIP rule's threshold/window/severity/active flag")
    public ResponseEntity<ApiResponse<PipRuleResponse>> updateRule(
            @PathVariable("code") PipRuleCode ruleCode, @Valid @RequestBody UpdatePipRuleRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(pipService.updateRule(callerUserId, ruleCode, request)));
    }
}
