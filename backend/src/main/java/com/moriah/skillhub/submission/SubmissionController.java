package com.moriah.skillhub.submission;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.submission.dto.CreateSubmissionRequest;
import com.moriah.skillhub.submission.dto.SubmissionResponse;
import com.moriah.skillhub.submission.entity.SubmissionStatus;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** build-plan.md feature 12. {@code create} is {@code hasRole('STUDENT')} only — a student
 * submitting their own work, same pattern as {@code TaskController.pull}; reads widened to
 * {@code TRAINER_PM}/{@code ADMIN}/{@code STUDENT}, same reasoning as {@code TaskController.list}. */
@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
@Tag(name = "Submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submit a PR for a task")
    public ResponseEntity<ApiResponse<SubmissionResponse>> create(
            @Valid @RequestBody CreateSubmissionRequest request, @CurrentUser Long callerUserId) {

        SubmissionResponse response = submissionService.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN','STUDENT')")
    @Operation(summary = "List submissions for a task")
    public ResponseEntity<ApiResponse<PageResponse<SubmissionResponse>>> list(
            @RequestParam Long taskId,
            @RequestParam(required = false) SubmissionStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(submissionService.list(taskId, status, pageable)));
    }
}
