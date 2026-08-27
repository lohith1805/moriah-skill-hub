package com.moriah.skillhub.batch;

import com.moriah.skillhub.batch.dto.AddStudentRequest;
import com.moriah.skillhub.batch.dto.BatchResponse;
import com.moriah.skillhub.batch.dto.CreateBatchRequest;
import com.moriah.skillhub.batch.dto.UpdateBatchRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No spec'd student-facing read — every method here is {@code TRAINER_PM}/{@code ADMIN} only.
 * Per-batch ownership (a {@code TRAINER_PM} may only manage their own batches; {@code ADMIN}
 * bypasses that) is enforced in {@link BatchService}, not here — it needs the loaded {@code
 * Batch} to check, which a static {@code @PreAuthorize} expression can't do. */
@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
@Tag(name = "Batches")
public class BatchController {

    private final BatchService batchService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Create a batch — the caller becomes its PM")
    public ResponseEntity<ApiResponse<BatchResponse>> create(
            @Valid @RequestBody CreateBatchRequest request, @CurrentUser Long callerUserId) {

        BatchResponse response = batchService.create(callerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "List batches")
    public ResponseEntity<ApiResponse<PageResponse<BatchResponse>>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(batchService.list(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Get one batch")
    public ResponseEntity<ApiResponse<BatchResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(batchService.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Update a batch")
    public ResponseEntity<ApiResponse<BatchResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateBatchRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(batchService.update(callerUserId, id, request)));
    }

    @PostMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Manually add a student to a batch")
    public ResponseEntity<ApiResponse<BatchResponse>> addStudent(
            @PathVariable Long id, @Valid @RequestBody AddStudentRequest request, @CurrentUser Long callerUserId) {

        return ResponseEntity.ok(ApiResponse.success(batchService.addStudent(callerUserId, id, request)));
    }

    /** {@code {userUuid}}, not {@code {userId}} — build-plan.md's endpoint list names the path
     * variable {@code userId}, but the public identifier for a user is always {@code uuid}
     * everywhere else in this project (User.java's own invariant); the placeholder name in the
     * spec is illustrative shorthand, not a literal contract. */
    @DeleteMapping("/{id}/students/{userUuid}")
    @PreAuthorize("hasAnyRole('TRAINER_PM','ADMIN')")
    @Operation(summary = "Remove a student from a batch (sets status to REASSIGNED, never deletes)")
    public ResponseEntity<ApiResponse<Void>> removeStudent(
            @PathVariable Long id, @PathVariable String userUuid, @CurrentUser Long callerUserId) {

        batchService.removeStudent(callerUserId, id, userUuid);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
