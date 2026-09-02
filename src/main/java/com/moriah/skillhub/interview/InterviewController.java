package com.moriah.skillhub.interview;

import com.moriah.skillhub.common.dto.ApiResponse;
import com.moriah.skillhub.common.dto.PageResponse;
import com.moriah.skillhub.common.security.CurrentUser;
import com.moriah.skillhub.interview.dto.InterviewResponse;
import com.moriah.skillhub.interview.dto.ScheduleInterviewRequest;
import com.moriah.skillhub.interview.dto.UpdateInterviewRequest;
import com.moriah.skillhub.interview.entity.InterviewStatus;
import com.moriah.skillhub.interview.entity.InterviewType;
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
 * Student Interviews (gap B1.8). Scheduling and management is TRAINER_PM/ADMIN; a STUDENT can
 * only read their own list via {@code /me}. Not in {@code SecurityConfig.PUBLIC_PATHS}.
 */
@RestController
@RequestMapping("/api/v1/interviews")
@RequiredArgsConstructor
@Tag(name = "Interviews")
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Schedule an interview for a student (starts SCHEDULED)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Interview scheduled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not TRAINER_PM/ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No user with this studentUuid")
    })
    public ResponseEntity<ApiResponse<InterviewResponse>> schedule(
            @Valid @RequestBody ScheduleInterviewRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(interviewService.schedule(request, callerUserId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "List interviews — optional status / type / studentUuid filters")
    public ResponseEntity<ApiResponse<PageResponse<InterviewResponse>>> list(
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) InterviewType type,
            @RequestParam(required = false) String studentUuid,
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                interviewService.listForStaff(status, type, studentUuid, pageable)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "The caller student's own interviews")
    public ResponseEntity<ApiResponse<PageResponse<InterviewResponse>>> mine(
            @CurrentUser Long callerUserId,
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(interviewService.listForStudent(callerUserId, pageable)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Reschedule / update an interview, including status, feedback and rating")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Interview updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not TRAINER_PM/ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No interview with this id")
    })
    public ResponseEntity<ApiResponse<InterviewResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateInterviewRequest request,
            @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(interviewService.update(id, request, callerUserId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Cancel an interview (status = CANCELLED) — never row-deletes")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Interview cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not TRAINER_PM/ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No interview with this id")
    })
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long id, @CurrentUser Long callerUserId) {
        interviewService.cancel(id, callerUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
