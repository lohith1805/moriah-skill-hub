package com.moriah.skillhub.project;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.project.dto.ChallengeSubmissionResponse;
import com.moriah.skillhub.project.dto.ReviewChallengeSubmissionRequest;
import com.moriah.skillhub.project.dto.SubmitChallengeRequest;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Bug-fix challenge submissions. A student rewrites the fix for a challenge and submits it; a
 * developer / trainer reads submissions and can review them. Split from {@link ChallengeController}
 * (which owns the challenge content itself) so the review surface stays small.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Bug Challenges")
public class ChallengeSubmissionController {

    private final ChallengeSubmissionService submissionService;

    @PostMapping("/api/v1/challenges/{challengeId}/submissions")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submit a rewritten fix for a bug-fix challenge")
    public ResponseEntity<ApiResponse<ChallengeSubmissionResponse>> submit(
            @PathVariable Long challengeId,
            @Valid @RequestBody SubmitChallengeRequest request,
            @CurrentUser Long callerUserId) {

        ChallengeSubmissionResponse response = submissionService.submit(callerUserId, challengeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/challenges/submissions/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "The calling student's own challenge submissions, newest first")
    public ResponseEntity<ApiResponse<PageResponse<ChallengeSubmissionResponse>>> mine(
            @CurrentUser Long callerUserId, @PageableDefault(size = 100) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(submissionService.mySubmissions(callerUserId, pageable)));
    }

    @GetMapping("/api/v1/challenges/{challengeId}/submissions")
    @PreAuthorize("hasAnyRole('DEVELOPER','TRAINER_PM','ADMIN')")
    @Operation(summary = "Every submission for one bug-fix challenge (review view)")
    public ResponseEntity<ApiResponse<PageResponse<ChallengeSubmissionResponse>>> forChallenge(
            @PathVariable Long challengeId, @PageableDefault(size = 100) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                submissionService.submissionsForChallenge(challengeId, pageable)));
    }

    @PutMapping("/api/v1/challenges/submissions/{id}/review")
    @PreAuthorize("hasAnyRole('DEVELOPER','TRAINER_PM','ADMIN')")
    @Operation(summary = "Record a verdict (ACCEPTED / NEEDS_WORK) plus optional feedback and score")
    public ResponseEntity<ApiResponse<ChallengeSubmissionResponse>> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewChallengeSubmissionRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(submissionService.review(callerUserId, id, request)));
    }
}
