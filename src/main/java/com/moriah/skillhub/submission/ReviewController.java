package com.moriah.skillhub.submission;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.sprint.TaskService;
import com.moriah.skillhub.sprint.dto.TaskResponse;
import com.moriah.skillhub.submission.dto.CreateReviewRequest;
import com.moriah.skillhub.submission.dto.CreateWeeklyReviewRequest;
import com.moriah.skillhub.submission.dto.ReviewResponse;
import com.moriah.skillhub.submission.dto.WeeklyReviewResponse;
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
import org.springframework.web.bind.annotation.RestController;

/** {@code TRAINER_PM}/{@code ADMIN} only, every method — reviewing is never a student action.
 * {@code queue} delegates straight to {@code TaskService.reviewQueue} — the queue is tasks, not
 * reviews (see {@code CodeReviewService}'s Javadoc). */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews")
public class ReviewController {

    private final TaskService taskService;
    private final CodeReviewService codeReviewService;
    private final WeeklyReviewService weeklyReviewService;

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "IN_REVIEW tasks awaiting the caller's review")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> queue(
            @CurrentUser Long callerUserId, @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(taskService.reviewQueue(callerUserId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Review a submission — score, verdict, comments")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @Valid @RequestBody CreateReviewRequest request, @CurrentUser Long callerUserId) {

        ReviewResponse response = codeReviewService.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/weekly")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Record a student's weekly qualitative rating")
    public ResponseEntity<ApiResponse<WeeklyReviewResponse>> createWeekly(
            @Valid @RequestBody CreateWeeklyReviewRequest request, @CurrentUser Long callerUserId) {

        WeeklyReviewResponse response = weeklyReviewService.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
