package com.moriah.skillhub.assessment;

import com.moriah.skillhub.assessment.dto.AssessmentResponse;
import com.moriah.skillhub.assessment.dto.CreateAssessmentRequest;
import com.moriah.skillhub.assessment.dto.QuizAttemptResponse;
import com.moriah.skillhub.assessment.dto.SubmitAttemptRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** build-plan.md feature 14. Mutation endpoints that create/own a quiz are {@code TRAINER_PM}/
 * {@code ADMIN} only, ownership enforced in the service layer via {@code
 * BatchService.requireOwnerOrAdmin} (same reasoning {@code SprintController}'s own Javadoc
 * gives). Attempt lifecycle endpoints ({@code startAttempt}/{@code submit}) are {@code STUDENT}
 * only; {@code getAttempt} is open to all three roles, ownership/PM-scoping enforced in {@code
 * QuizService#authorizeView}. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Assessments")
public class AssessmentController {

    private final QuizService quizService;

    @PostMapping("/api/v1/assessments")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Create an assessment (quiz) with its questions")
    public ResponseEntity<ApiResponse<AssessmentResponse>> create(
            @Valid @RequestBody CreateAssessmentRequest request, @CurrentUser Long callerUserId) {

        AssessmentResponse response = quizService.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/assessments")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN','STUDENT')")
    @Operation(summary = "List assessments for a batch")
    public ResponseEntity<ApiResponse<PageResponse<AssessmentResponse>>> list(
            @RequestParam Long batchId, @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(quizService.list(batchId, pageable)));
    }

    @PostMapping("/api/v1/assessments/{id}/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Start (or resume) an attempt at an assessment")
    public ResponseEntity<ApiResponse<QuizAttemptResponse>> startAttempt(
            @PathVariable Long id, @CurrentUser Long callerUserId) {

        QuizAttemptResponse response = quizService.startAttempt(callerUserId, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/assessments/attempts/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN','STUDENT')")
    @Operation(summary = "Get an attempt — its questions while in progress, its graded result once terminal")
    public ResponseEntity<ApiResponse<QuizAttemptResponse>> getAttempt(
            @PathVariable Long id, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(quizService.getAttempt(callerUserId, id)));
    }

    @PostMapping("/api/v1/assessments/attempts/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submit an attempt for grading")
    public ResponseEntity<ApiResponse<QuizAttemptResponse>> submit(
            @PathVariable Long id, @Valid @RequestBody SubmitAttemptRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(quizService.submit(callerUserId, id, request)));
    }
}
